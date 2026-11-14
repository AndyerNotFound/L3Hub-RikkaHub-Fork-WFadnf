package me.rerere.ai.provider

import java.time.Instant

/**
 * 变价规则的自定义表达式。
 *
 * 语法是常规的数值表达式：+ - * / %、比较、&& || !、括号和数字（支持 200k / 1m 后缀），
 * 结果为非零即命中。可用变量见 [variables]。
 */
internal object PriceRuleExpression {
    fun matches(expression: String, context: PriceRuleContext): Boolean {
        val value = runCatching {
            Parser(expression, variables(context)).parse()
        }.getOrNull() ?: return false
        return value.isFinite() && value != 0.0
    }

    fun errorPosition(expression: String): Int? = try {
        Parser(expression, variables(PriceRuleContext(promptTokens = 1))).parse()
        null
    } catch (e: PriceExpressionException) {
        e.position
    } catch (_: Throwable) {
        0
    }

    private fun variables(context: PriceRuleContext): Map<String, Double> {
        val time = Instant.ofEpochMilli(context.atMillis).atZone(context.zoneId)
        val totalTokens = context.promptTokens + context.completionTokens
        return buildMap {
            put("prompttokens", context.promptTokens.toDouble())
            put("inputtokens", context.promptTokens.toDouble())
            put("completiontokens", context.completionTokens.toDouble())
            put("outputtokens", context.completionTokens.toDouble())
            put("cachedtokens", context.cachedTokens.toDouble())
            put("totaltokens", totalTokens.toDouble())
            put("hour", time.hour.toDouble())
            put("minute", time.minute.toDouble())
            put("second", time.second.toDouble())
            put("minuteofday", (time.hour * 60 + time.minute).toDouble())
            put("weekday", time.dayOfWeek.value.toDouble())
            put("day", time.dayOfMonth.toDouble())
            put("month", time.monthValue.toDouble())
            put("year", time.year.toDouble())
            put("timestamp", context.atMillis.toDouble())
        }
    }

    private fun normalize(name: String): String =
        name.filter { it.isLetterOrDigit() }.lowercase()

    private class Parser(
        private val source: String,
        private val variables: Map<String, Double>,
    ) {
        private var index = 0
        private var token = nextToken()

        init {
            if (source.length > MAX_PRICE_RULE_EXPRESSION_LENGTH) {
                throw PriceExpressionException(MAX_PRICE_RULE_EXPRESSION_LENGTH)
            }
        }

        fun parse(): Double {
            val value = parseOr()
            if (token.type != TokenType.EOF) throw PriceExpressionException(token.position)
            return value
        }

        private fun parseOr(): Double {
            var left = parseAnd()
            while (token.type == TokenType.OR) {
                advance()
                val right = parseAnd()
                left = if (left != 0.0 || right != 0.0) 1.0 else 0.0
            }
            return left
        }

        private fun parseAnd(): Double {
            var left = parseComparison()
            while (token.type == TokenType.AND) {
                advance()
                val right = parseComparison()
                left = if (left != 0.0 && right != 0.0) 1.0 else 0.0
            }
            return left
        }

        private fun parseComparison(): Double {
            var left = parseAdditive()
            while (token.type in COMPARISONS) {
                val op = token.type
                advance()
                val right = parseAdditive()
                val matched = when (op) {
                    TokenType.GT -> left > right
                    TokenType.GE -> left >= right
                    TokenType.LT -> left < right
                    TokenType.LE -> left <= right
                    TokenType.EQ -> left == right
                    TokenType.NE -> left != right
                    else -> false
                }
                left = if (matched) 1.0 else 0.0
            }
            return left
        }

        private fun parseAdditive(): Double {
            var left = parseMultiplicative()
            while (token.type == TokenType.PLUS || token.type == TokenType.MINUS) {
                val op = token.type
                advance()
                val right = parseMultiplicative()
                left = if (op == TokenType.PLUS) left + right else left - right
            }
            return left
        }

        private fun parseMultiplicative(): Double {
            var left = parseUnary()
            while (token.type in MULTIPLICATIONS) {
                val op = token.type
                advance()
                val right = parseUnary()
                left = when (op) {
                    TokenType.STAR -> left * right
                    TokenType.SLASH -> left / right
                    TokenType.PERCENT -> left % right
                    else -> left
                }
            }
            return left
        }

        private fun parseUnary(): Double {
            return when (token.type) {
                TokenType.NOT -> {
                    advance()
                    if (parseUnary() == 0.0) 1.0 else 0.0
                }
                TokenType.MINUS -> {
                    advance()
                    -parseUnary()
                }
                TokenType.PLUS -> {
                    advance()
                    parseUnary()
                }
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double = when (token.type) {
            TokenType.NUMBER -> {
                val value = token.number
                advance()
                value
            }
            TokenType.IDENT -> {
                val position = token.position
                val name = normalize(token.text)
                advance()
                when (name) {
                    "true" -> 1.0
                    "false" -> 0.0
                    else -> variables[name] ?: throw PriceExpressionException(position)
                }
            }
            TokenType.LPAREN -> {
                advance()
                val value = parseOr()
                if (token.type != TokenType.RPAREN) throw PriceExpressionException(token.position)
                advance()
                value
            }
            else -> throw PriceExpressionException(token.position)
        }

        private fun advance() {
            token = nextToken()
        }

        private fun nextToken(): Token {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) return Token(TokenType.EOF, position = index)
            val start = index
            val char = source[index]
            if (char.isDigit() || (char == '.' && index + 1 < source.length && source[index + 1].isDigit())) {
                index++
                while (index < source.length && source[index].isDigit()) index++
                if (index < source.length && source[index] == '.') {
                    index++
                    while (index < source.length && source[index].isDigit()) index++
                }
                var value = source.substring(start, index).toDoubleOrNull()
                    ?: throw PriceExpressionException(start)
                if (index < source.length) {
                    when (source[index]) {
                        'k', 'K' -> {
                            value *= 1_000.0
                            index++
                        }
                        'm', 'M' -> {
                            value *= 1_000_000.0
                            index++
                        }
                    }
                }
                return Token(TokenType.NUMBER, number = value, position = start)
            }
            if (char.isLetter() || char == '_') {
                index++
                while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                val text = source.substring(start, index)
                val type = when (text.lowercase()) {
                    "and" -> TokenType.AND
                    "or" -> TokenType.OR
                    "not" -> TokenType.NOT
                    else -> TokenType.IDENT
                }
                return Token(type, text = text, position = start)
            }
            val two = if (index + 1 < source.length) source.substring(index, index + 2) else ""
            val twoChar = when (two) {
                "&&" -> TokenType.AND
                "||" -> TokenType.OR
                ">=" -> TokenType.GE
                "<=" -> TokenType.LE
                "==" -> TokenType.EQ
                "!=" -> TokenType.NE
                else -> null
            }
            if (twoChar != null) {
                index += 2
                return Token(twoChar, position = start)
            }
            index++
            val single = when (char) {
                '+' -> TokenType.PLUS
                '-' -> TokenType.MINUS
                '*' -> TokenType.STAR
                '/' -> TokenType.SLASH
                '%' -> TokenType.PERCENT
                '(' -> TokenType.LPAREN
                ')' -> TokenType.RPAREN
                '>' -> TokenType.GT
                '<' -> TokenType.LT
                '!' -> TokenType.NOT
                else -> throw PriceExpressionException(start)
            }
            return Token(single, position = start)
        }

        private companion object {
            val COMPARISONS = setOf(
                TokenType.GT,
                TokenType.GE,
                TokenType.LT,
                TokenType.LE,
                TokenType.EQ,
                TokenType.NE,
            )
            val MULTIPLICATIONS = setOf(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)
        }
    }

    private enum class TokenType {
        NUMBER,
        IDENT,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        LPAREN,
        RPAREN,
        GT,
        GE,
        LT,
        LE,
        EQ,
        NE,
        AND,
        OR,
        NOT,
        EOF,
    }

    private data class Token(
        val type: TokenType,
        val text: String = "",
        val number: Double = 0.0,
        val position: Int = 0,
    )

    private class PriceExpressionException(val position: Int) : Exception()
}
