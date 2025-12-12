package ru.adan.silmaril.misc

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import ru.adan.silmaril.platform.createLogger

private val logger = createLogger("ru.adan.silmaril.misc.ExpressionEvaluator")

// Regex patterns for finding $math( and $if(
private val mathStartPattern = "\$math("
private val ifStartPattern = "\$if("

// EvalEx configuration - use standard math context
private val evalExConfig = ExpressionConfiguration.defaultConfiguration()

/**
 * Evaluates all $math(...) and $if(...)(...)(...)  expressions in the input string.
 * Supports nested calls and references to variables.
 *
 * $math(expression) - evaluates math/comparison expression and returns result
 * $if(condition)(trueValue)(falseValue) - evaluates condition, returns trueValue if true, falseValue otherwise
 *
 * @param input The input string containing expressions
 * @param variableResolver A function that resolves variable names to their values (without the $ prefix)
 * @return The input string with all expressions replaced with their results
 */
fun evaluateMathExpressions(input: String, variableResolver: (String) -> String?): String {
    var result = input
    var iterations = 0
    val maxIterations = 10 // Prevent infinite loops

    // Keep evaluating until no more expressions are found
    // This handles nested calls - inner ones get evaluated first
    while ((result.contains(mathStartPattern) || result.contains(ifStartPattern)) && iterations < maxIterations) {
        result = evaluateSinglePass(result, variableResolver)
        iterations++
    }

    return result
}

/**
 * Single pass of expression evaluation - finds innermost $math() and $if()()() calls and evaluates them.
 */
private fun evaluateSinglePass(input: String, variableResolver: (String) -> String?): String {
    val sb = StringBuilder()
    var i = 0

    while (i < input.length) {
        when {
            // Look for $math( starting at position i
            input.regionMatches(i, mathStartPattern, 0, 6) -> {
                val startContent = i + 6 // Position after "$math("
                val endParen = findMatchingParen(input, startContent - 1)

                if (endParen != -1) {
                    val expression = input.substring(startContent, endParen)
                    // If expression contains nested $math() or $if(), skip for now
                    // Let inner expressions be evaluated first in subsequent passes
                    if (expression.contains(mathStartPattern) || expression.contains(ifStartPattern)) {
                        sb.append(input[i])
                        i++
                    } else {
                        val evaluated = evaluateMathExpression(expression, variableResolver)
                        sb.append(evaluated)
                        i = endParen + 1
                    }
                } else {
                    sb.append(input[i])
                    i++
                }
            }
            // Look for $if( starting at position i
            input.regionMatches(i, ifStartPattern, 0, 4) -> {
                val result = parseAndEvaluateIf(input, i, variableResolver)
                if (result != null) {
                    sb.append(result.first)
                    i = result.second
                } else {
                    sb.append(input[i])
                    i++
                }
            }
            else -> {
                sb.append(input[i])
                i++
            }
        }
    }

    return sb.toString()
}

/**
 * Parse and evaluate $if(condition)(trueValue)(falseValue)
 * Returns pair of (result string, new index after the expression) or null if parsing failed.
 * Returns null if any part contains nested $math() or $if() that need to be evaluated first.
 */
private fun parseAndEvaluateIf(input: String, startIndex: Int, variableResolver: (String) -> String?): Pair<String, Int>? {
    // startIndex points to '$' in "$if("
    val conditionStart = startIndex + 4 // Position after "$if("

    // Find end of condition
    val conditionEnd = findMatchingParen(input, conditionStart - 1)
    if (conditionEnd == -1) return null

    // Check for opening paren of true value
    if (conditionEnd + 1 >= input.length || input[conditionEnd + 1] != '(') return null
    val trueStart = conditionEnd + 2 // Position after "("
    val trueEnd = findMatchingParen(input, conditionEnd + 1)
    if (trueEnd == -1) return null

    // Check for opening paren of false value
    if (trueEnd + 1 >= input.length || input[trueEnd + 1] != '(') return null
    val falseStart = trueEnd + 2 // Position after "("
    val falseEnd = findMatchingParen(input, trueEnd + 1)
    if (falseEnd == -1) return null

    val condition = input.substring(conditionStart, conditionEnd)
    val trueValue = input.substring(trueStart, trueEnd)
    val falseValue = input.substring(falseStart, falseEnd)

    // If any part contains nested expressions, skip this $if for now
    // Let the inner expressions be evaluated first in subsequent passes
    if (condition.contains(mathStartPattern) || condition.contains(ifStartPattern) ||
        trueValue.contains(mathStartPattern) || trueValue.contains(ifStartPattern) ||
        falseValue.contains(mathStartPattern) || falseValue.contains(ifStartPattern)) {
        return null
    }

    val result = evaluateIfExpression(condition, trueValue, falseValue, variableResolver)
    return Pair(result, falseEnd + 1)
}

/**
 * Find the matching closing parenthesis for the opening one at startIndex.
 * startIndex should point to the '(' character.
 */
private fun findMatchingParen(input: String, startIndex: Int): Int {
    if (startIndex >= input.length || input[startIndex] != '(') return -1

    var depth = 1
    var i = startIndex + 1

    while (i < input.length && depth > 0) {
        when (input[i]) {
            '(' -> depth++
            ')' -> depth--
        }
        if (depth > 0) i++
    }

    return if (depth == 0) i else -1
}

/**
 * Resolve variables in an expression string.
 * Non-numeric values are automatically quoted for EvalEx string comparison.
 */
private fun resolveVariables(expression: String, variableResolver: (String) -> String?): String {
    // Match $varName but not $math( or $if(
    val varRegex = Regex("""\${'$'}(?!math\(|if\()[\p{L}\p{N}_]+""")
    return varRegex.replace(expression) { matchResult ->
        val varName = matchResult.value.substring(1) // Remove $
        val value = variableResolver(varName) ?: return@replace matchResult.value
        // If value is numeric, return as-is; otherwise quote it for EvalEx string comparison
        if (value.toDoubleOrNull() != null) {
            value
        } else {
            // Escape any quotes in the value and wrap in quotes
            "\"${value.replace("\"", "\\\"")}\""
        }
    }
}

/**
 * Evaluate a math expression using EvalEx.
 */
private fun evaluateMathExpression(expression: String, variableResolver: (String) -> String?): String {
    val resolvedExpression = resolveVariables(expression, variableResolver)

    return try {
        val expr = Expression(resolvedExpression, evalExConfig)
        val result = expr.evaluate()

        // Check if result is a number or boolean
        when {
            result.isBooleanValue -> result.booleanValue.toString()
            result.isNumberValue -> {
                val num = result.numberValue
                // Format nicely - whole numbers without decimals
                if (num.stripTrailingZeros().scale() <= 0) {
                    num.toBigInteger().toString()
                } else {
                    num.stripTrailingZeros().toPlainString()
                }
            }
            result.isStringValue -> result.stringValue
            else -> result.toString()
        }
    } catch (e: Exception) {
        logger.warn { "Error evaluating math expression: $resolvedExpression - ${e.message}" }
        "\$math($expression)" // Return original on error
    }
}

/**
 * Evaluate an if expression - returns trueValue if condition is true, falseValue otherwise.
 */
private fun evaluateIfExpression(
    condition: String,
    trueValue: String,
    falseValue: String,
    variableResolver: (String) -> String?
): String {
    val resolvedCondition = resolveVariables(condition, variableResolver)

    return try {
        val expr = Expression(resolvedCondition, evalExConfig)
        val result = expr.evaluate()

        val isTrue = when {
            result.isBooleanValue -> result.booleanValue
            result.isNumberValue -> result.numberValue.toDouble() != 0.0
            result.isStringValue -> result.stringValue.isNotEmpty()
            else -> false
        }

        if (isTrue) trueValue else falseValue
    } catch (e: Exception) {
        logger.warn { "Error evaluating if condition: $resolvedCondition - ${e.message}" }
        "\$if($condition)($trueValue)($falseValue)" // Return original on error
    }
}
