package ru.natk.yourfit

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Exercise(val name: String, val sets: Int, val reps: Int, val seconds: Int, val image: String, val tips: List<String>) {
    val prescription: String get() = if (seconds > 0) "$sets × $seconds сек" else "$sets × $reps повторений"
}
data class Workout(val id: String, val name: String, val category: String, val minutes: Int,
    val level: String, val equipment: String, val description: String, val muscles: String,
    val image: String, val exercises: List<Exercise>)

object Catalog {
    val images = mapOf("home_pushups" to R.drawable.home_pushups, "technique_plank" to R.drawable.technique_plank,
        "home_squat" to R.drawable.home_squat, "home_crunch" to R.drawable.home_crunch,
        "street_pullup" to R.drawable.street_pullup, "street_dips" to R.drawable.street_dips,
        "gym_benchpress" to R.drawable.gym_benchpress, "gym_squat" to R.drawable.gym_squat, "gym_deadlift" to R.drawable.gym_deadlift)
    private fun ex(name: String, image: String, reps: Int = 10, seconds: Int = 0, sets: Int = 2, vararg tips: String) =
        Exercise(name, sets, reps, seconds, image, tips.toList())
    val push = ex("Отжимания", "home_pushups", 8, 0, 2, "Ладони немного шире плеч.", "Сохраняй прямую линию корпуса.", "Опускайся и поднимайся плавно.")
    val plank = ex("Планка на прямых руках", "technique_plank", 0, 30, 2, "Ладони находятся под плечами.", "Не прогибай поясницу.", "Дыши спокойно на протяжении подхода.")
    val squat = ex("Приседания", "home_squat", 12, 0, 2, "Поставь стопы примерно на ширине плеч.", "Направляй колени по линии стоп.", "Сохраняй опору на всю стопу.")
    val crunch = ex("Скручивания", "home_crunch", 12, 0, 2, "Согни колени, поставь стопы на пол.", "Плавно подними плечи от коврика.", "Не тяни голову руками.")
    val pull = ex("Подтягивания", "street_pullup", 4, 0, 2, "Возьмись за перекладину удобным хватом.", "Поднимайся без раскачивания.", "Опускайся подконтрольно; при необходимости используй резинку.")
    val dips = ex("Отжимания на брусьях", "street_dips", 5, 0, 2, "Удерживай плечи устойчиво.", "Опускайся до комфортной глубины.", "Поднимайся без рывка и раскачивания.")
    val bench = ex("Жим лёжа", "gym_benchpress", 8, 0, 3, "Стопы устойчиво стоят на полу.", "Опускай гриф плавно, удерживая лопатки.", "Используй страховку и посильный вес.")
    val barSquat = ex("Присед со штангой", "gym_squat", 8, 0, 3, "Настрой страховочные упоры стойки.", "Держи корпус устойчиво и стопы на полу.", "Выбери вес, с которым контролируешь движение.")
    val deadlift = ex("Становая тяга", "gym_deadlift", 6, 0, 3, "Расположи гриф близко к голеням.", "Сохраняй нейтральное положение спины.", "Поднимай вес плавно, разгибая ноги и таз.")
    private fun w(id: String, name: String, category: String, mins: Int, equipment: String, muscles: String, vararg exercises: Exercise) =
        Workout(id, name, category, mins, if (category == "Дом") "Начальный" else "Средний", equipment,
            when (category) { "Дом" -> "Короткая тренировка дома в удобном темпе. Перед началом разомнись и подготовь свободное место."; "Улица" -> "Тренировка на площадке с собственным весом. Выбирай посильное число повторений."; else -> "Базовая силовая тренировка. Начни с разминки и небольшого веса, уделяя внимание технике." },
            muscles, exercises.first().image, exercises.toList())
    val workouts = listOf(
        w("abs", "Пресс за 30 дней", "Дом", 10, "Коврик", "Пресс · Корпус", crunch, plank),
        w("push", "Отжимания с нуля", "Дом", 10, "Без инвентаря", "Грудь · Трицепс · Корпус", push.copy(reps=5), plank.copy(seconds=20)),
        w("full", "Всё тело", "Дом", 15, "Коврик", "Ноги · Грудь · Корпус", squat, push, plank),
        w("core", "Сильный корпус", "Дом", 10, "Коврик", "Корпус · Пресс", plank, crunch),
        w("legs", "Ноги дома", "Дом", 10, "Без инвентаря", "Ноги · Ягодицы", squat.copy(sets=3, reps=15)),
        w("pull", "Турник с нуля", "Улица", 10, "Турник · Резинка по необходимости", "Спина · Бицепс", pull.copy(sets=3, reps=3)),
        w("dips", "Брусья", "Улица", 10, "Брусья", "Грудь · Трицепс", dips.copy(sets=3)),
        w("bars", "Турник + брусья", "Улица", 15, "Турник и брусья", "Спина · Грудь · Руки", pull, dips),
        w("base", "Силовая база", "Зал", 25, "Штанга · Стойка · Скамья", "Ноги · Грудь · Спина", barSquat, bench, deadlift),
        w("bench", "Жим лёжа", "Зал", 15, "Штанга · Скамья · Страховка", "Грудь · Трицепс", bench),
        w("squat", "Присед", "Зал", 15, "Штанга · Стойка", "Ноги · Ягодицы", barSquat),
        w("deadlift", "Становая тяга", "Зал", 15, "Штанга", "Спина · Ноги · Ягодицы", deadlift)
    )
    fun get(id: String) = workouts.first { it.id == id }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

/** One account document is committed atomically, including session and completion history. */
class FitnessStore(context: Context, name: String = "yourfit_local_v2") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private var root = JSONObject(prefs.getString("data", "{\"accounts\":[]}")!!)
    val account: JSONObject? get() = root.getJSONArray("accounts").objects().firstOrNull { it.getString("id") == root.optString("active") }
    fun save() { check(prefs.edit().putString("data", root.toString()).commit()) { "Не удалось сохранить данные" } }
    private fun hash(password: String, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 60000, 256)
        return try { SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun register(name: String, email: String, password: String): Boolean {
        val normalized = email.trim().lowercase(java.util.Locale.ROOT)
        val accounts = root.getJSONArray("accounts")
        if (accounts.objects().any { it.getString("email") == normalized }) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algorithm = if (android.os.Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val id = UUID.randomUUID().toString()
        accounts.put(JSONObject().put("id", id).put("name", name.trim()).put("email", normalized)
            .put("salt", encode(salt)).put("hash", encode(hash(password, salt, algorithm))).put("algorithm", algorithm).put("weight", 0)
            .put("goals", JSONArray()).put("history", JSONArray()).put("schedule", JSONArray())
            .put("reminderDays", JSONArray()).put("reminderTime", "19:00").put("notes", ""))
        root.put("active", id); save(); return true
    }
    fun login(email: String, password: String): Boolean {
        val a = root.getJSONArray("accounts").objects().firstOrNull { it.getString("email") == email.trim().lowercase(java.util.Locale.ROOT) } ?: return false
        val valid = MessageDigest.isEqual(Base64.decode(a.getString("hash"), Base64.NO_WRAP), hash(password, Base64.decode(a.getString("salt"), Base64.NO_WRAP), a.optString("algorithm", "PBKDF2WithHmacSHA256")))
        if (valid) { root.put("active", a.getString("id")); save() }; return valid
    }
    fun logout() { root.remove("active"); save() }
    fun session(): Session? = account?.optJSONObject("session")?.let { Session.from(it) }
    fun session(value: Session?) { account?.let { if (value == null) it.remove("session") else it.put("session", value.json()); save() } }
    fun complete(s: Session, now: Long = System.currentTimeMillis()): Boolean {
        val a = account ?: return false
        val history = a.getJSONArray("history")
        if (history.objects().any { it.getString("id") == s.id }) return false
        val weight = a.optDouble("weight", 0.0)
        // Rough energy estimate from active time only; no assumed user weight.
        val kcal = if (weight > 0) 3.5 * 3.5 * weight / 200.0 * (s.activeMs / 60000.0) else 0.0
        history.put(JSONObject().put("id", s.id).put("workout", s.workout).put("date", now)
            .put("activeMs", s.activeMs).put("calories", kcal).put("weight", weight).put("sets", s.results))
        a.remove("session"); save(); return true
    }
}

data class Session(val workout: String, val id: String = UUID.randomUUID().toString(),
    var exercise: Int = 0, var set: Int = 0, var rest: Boolean = false, var running: Boolean = false,
    var remainingMs: Long = 0, var setMs: Long = 0, var activeMs: Long = 0, var restTotal: Long = 30000,
    var results: JSONArray = JSONArray()) {
    val plan get() = Catalog.get(workout)
    val current get() = plan.exercises[exercise]
    fun tick(delta: Long) {
        if (!running) return
        val used = if (rest || current.seconds > 0) delta.coerceAtMost(remainingMs).coerceAtLeast(0) else delta.coerceAtLeast(0)
        if (rest || current.seconds > 0) remainingMs = (remainingMs-used).coerceAtLeast(0)
        if (!rest) { activeMs += used; setMs += used }
        if ((rest || current.seconds > 0) && remainingMs == 0L) running = false
    }
    fun prepare() { rest=false; running=false; setMs=0; remainingMs=current.seconds*1000L }
    /** Returns true only once all prescribed sets have been recorded. */
    fun finishSet(reps: Int): Boolean {
        results.put(JSONObject().put("exercise", current.name).put("set", set+1).put("reps", reps).put("ms", setMs))
        running=false
        if (set+1 < current.sets) set++ else { set=0; exercise++ }
        if (exercise >= plan.exercises.size) return true
        rest=true; remainingMs=30000; restTotal=30000; setMs=0; running=true; return false
    }
    fun json() = JSONObject().put("workout",workout).put("id",id).put("exercise",exercise).put("set",set)
        .put("rest",rest).put("running",false).put("remaining",remainingMs).put("setMs",setMs)
        .put("activeMs",activeMs).put("restTotal",restTotal).put("results",results)
    companion object {
        fun from(j: JSONObject) = Session(j.getString("workout"),j.getString("id"),j.getInt("exercise"),j.getInt("set"),j.getBoolean("rest"),false,
            j.getLong("remaining"),j.getLong("setMs"),j.getLong("activeMs"),j.optLong("restTotal",30000),j.optJSONArray("results")?:JSONArray())
        fun start(workout: String) = Session(workout).apply { prepare() }
    }
}
