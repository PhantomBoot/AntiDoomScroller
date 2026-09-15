package com.antidoomscroller.core.lock

import com.antidoomscroller.core.model.ChallengeDifficulty
import kotlin.random.Random

/** One problem and its answer. The answer never leaves the device; it is recomputed from the seed. */
data class MathProblem(
    val prompt: String,
    val answer: Long,
    val difficulty: ChallengeDifficulty,
) {
    fun isCorrect(input: String): Boolean = input.trim().toLongOrNull() == answer
}

/** The full set the user has to clear in one sitting. */
data class Challenge(
    val seed: Long,
    val problems: List<MathProblem>,
)

/**
 * Generates the arithmetic gate in front of turning the filter off.
 *
 * Seeded on purpose: the same seed always produces the same problems, so closing the app and
 * reopening it cannot reroll a hard problem into an easy one.
 */
object ChallengeGenerator {

    fun generate(seed: Long, count: Int, difficulty: ChallengeDifficulty): Challenge {
        val random = Random(seed)
        val problems = (0 until count.coerceIn(1, 10)).map { problem(random, difficulty) }
        return Challenge(seed, problems)
    }

    fun problem(random: Random, difficulty: ChallengeDifficulty): MathProblem = when (difficulty) {
        ChallengeDifficulty.MEDIUM -> mediumProblem(random)
        ChallengeDifficulty.HARD -> hardProblem(random)
        ChallengeDifficulty.BRUTAL -> brutalProblem(random)
    }

    private fun mediumProblem(random: Random): MathProblem {
        val a = random.nextInt(12, 40)
        val b = random.nextInt(12, 40)
        val c = random.nextInt(50, 400)
        return MathProblem(
            prompt = "$a × $b − $c",
            answer = a.toLong() * b - c,
            difficulty = ChallengeDifficulty.MEDIUM,
        )
    }

    private fun hardProblem(random: Random): MathProblem {
        return when (random.nextInt(4)) {
            0 -> {
                val a = random.nextInt(21, 99)
                val b = random.nextInt(21, 99)
                val c = random.nextInt(11, 49)
                val d = random.nextInt(11, 49)
                MathProblem("($a × $b) − ($c × $d)", a.toLong() * b - c.toLong() * d, ChallengeDifficulty.HARD)
            }

            1 -> {
                val a = random.nextInt(101, 999)
                val b = random.nextInt(13, 97)
                val m = random.nextInt(7, 97)
                MathProblem(
                    "The remainder when $a × $b is divided by $m",
                    (a.toLong() * b) % m,
                    ChallengeDifficulty.HARD,
                )
            }

            2 -> {
                val a = random.nextInt(24, 96)
                val b = random.nextInt(24, 96)
                MathProblem(
                    "The sum of the digits of $a × $b",
                    digitSum(a.toLong() * b),
                    ChallengeDifficulty.HARD,
                )
            }

            else -> {
                val a = random.nextInt(31, 89)
                val b = random.nextInt(11, 29)
                val c = random.nextInt(101, 799)
                MathProblem("$a² − $b × $c", a.toLong() * a - b.toLong() * c, ChallengeDifficulty.HARD)
            }
        }
    }

    private fun brutalProblem(random: Random): MathProblem {
        return when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(211, 999)
                val b = random.nextInt(211, 999)
                val c = random.nextInt(1001, 9999)
                val m = random.nextInt(37, 397)
                val value = (a.toLong() * b + c) % m
                MathProblem(
                    "The remainder when ($a × $b + $c) is divided by $m",
                    value,
                    ChallengeDifficulty.BRUTAL,
                )
            }

            1 -> {
                val base = random.nextInt(7, 23)
                val exponent = random.nextInt(4, 7)
                val m = random.nextInt(97, 997)
                var value = 1L
                repeat(exponent) { value = value * base % m }
                MathProblem(
                    "The remainder when $base^$exponent is divided by $m",
                    value,
                    ChallengeDifficulty.BRUTAL,
                )
            }

            else -> {
                val a = random.nextInt(1001, 9999)
                val b = random.nextInt(101, 999)
                val c = random.nextInt(11, 99)
                MathProblem(
                    "($a − $b) × $c, then the sum of that result's digits",
                    digitSum((a.toLong() - b) * c),
                    ChallengeDifficulty.BRUTAL,
                )
            }
        }
    }

    private fun digitSum(value: Long): Long {
        var remaining = kotlin.math.abs(value)
        var sum = 0L
        while (remaining > 0) {
            sum += remaining % 10
            remaining /= 10
        }
        return sum
    }
}
