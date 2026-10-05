package ru.natk.yourfit

import android.test.AndroidTestCase
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.widget.CheckBox
import android.widget.ScrollView
import android.os.SystemClock
import android.content.pm.ActivityInfo
import org.json.JSONArray

@Suppress("DEPRECATION")
class FitnessTests : AndroidTestCase() {
    fun testCredentialsAndAccountIsolation() {
        val name="test_accounts_"+System.nanoTime()
        val s=FitnessStore(context,name)
        assertTrue(s.register("Anna","Anna@example.test","secret123"))
        assertFalse(s.register("Other","anna@example.test","secret123"))
        val salt=s.account!!.getString("salt")
        assertFalse(s.account!!.toString().contains("secret123"))
        s.account!!.put("weight",72.5).put("goals",JSONArray(listOf("Быть в форме"))); s.save()
        s.logout(); assertNull(s.account)
        assertFalse(s.login("anna@example.test","wrong123"))
        assertTrue(s.login("ANNA@example.test","secret123"))
        assertEquals(72.5,s.account!!.getDouble("weight"))
        s.logout(); assertTrue(s.register("Boris","boris@example.test","secret123"))
        assertFalse(salt==s.account!!.getString("salt")); assertEquals(0,s.account!!.getJSONArray("history").length())
        s.logout(); val reload=FitnessStore(context,name)
        assertTrue(reload.login("anna@example.test","secret123")); assertEquals("Быть в форме",reload.account!!.getJSONArray("goals").getString(0))
    }
    fun testTimerPauseClampRestAndPersistence() {
        val s=Session.start("core")
        s.tick(1000); assertEquals(30000L,s.remainingMs)
        s.running=true; s.tick(9000); assertEquals(21000L,s.remainingMs); assertEquals(9000L,s.activeMs)
        s.running=false; s.tick(40000); assertEquals(21000L,s.remainingMs)
        val restored=Session.from(s.json()); assertFalse(restored.running); assertEquals(9000L,restored.activeMs)
        restored.running=true; restored.tick(50000); assertEquals(0L,restored.remainingMs); assertEquals(30000L,restored.activeMs); assertFalse(restored.running)
        assertFalse(restored.finishSet(0)); assertTrue(restored.rest); assertEquals(1,restored.set)
        restored.tick(10000); assertEquals(30000L,restored.activeMs); assertEquals(20000L,restored.remainingMs)
        restored.running=false; restored.remainingMs+=30000; restored.restTotal+=30000; restored.tick(2000); assertEquals(50000L,restored.remainingMs)
        restored.prepare(); assertFalse(restored.rest); assertEquals(30000L,restored.remainingMs)
    }
    fun testCompletionIsAtomicAndExactlyOnce() {
        val name="test_history_"+System.nanoTime(); val store=FitnessStore(context,name)
        store.register("Test","test@example.test","secret123"); store.account!!.put("weight",70); store.save()
        val s=Session.start("legs")
        repeat(3) { index -> s.running=true; s.tick(20000); assertEquals(index==2,s.finishSet(15)); if(index<2) s.prepare() }
        assertEquals(60000L,s.activeMs); assertEquals(3,s.results.length())
        assertTrue(store.complete(s,1700000000000)); assertFalse(store.complete(s,1700000000000))
        val saved=FitnessStore(context,name); assertNull(saved.session())
        val h=saved.account!!.getJSONArray("history"); assertEquals(1,h.length()); assertEquals(60000L,h.getJSONObject(0).getLong("activeMs")); assertTrue(h.getJSONObject(0).getDouble("calories")>0)
    }
    fun testCatalogResourcesAndExerciseCoverage() {
        assertEquals(12,Catalog.workouts.size); assertEquals(12,Catalog.workouts.map { it.id }.distinct().size)
        assertEquals(5,Catalog.workouts.count { it.category=="Дом" }); assertEquals(3,Catalog.workouts.count { it.category=="Улица" })
        Catalog.workouts.forEach { w -> assertTrue(w.exercises.isNotEmpty()); w.exercises.forEach { e -> assertTrue(e.sets>0); assertTrue(e.reps>0 || e.seconds>0); assertTrue(context.resources.getIdentifier(e.image,"drawable",context.packageName)!=0); assertEquals(3,e.tips.size) } }
    }
}

@Suppress("DEPRECATION")
class FlowTests : ActivityInstrumentationTestCase2<MainActivity>("ru.natk.yourfit.qa",MainActivity::class.java) {
    private lateinit var app: MainActivity
    private fun ui(block: ()->Unit) { instrumentation.runOnMainSync(block); instrumentation.waitForIdleSync() }
    private fun all(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun views()=all(app.window.decorView)
    private fun click(text: String) {
        ui {
            val found=views().firstOrNull { it is TextView && it.text.toString()==text } ?: throw AssertionError("Missing text: $text")
            var target: View=found
            while(!target.isClickable && target.parent is View) target=target.parent as View
            assertTrue("Disabled: $text",target.isEnabled); assertTrue("Not clickable: $text",target.performClick())
        }
        SystemClock.sleep(220)
    }
    private fun contains(text: String) { ui { assertTrue("Missing: $text",views().any { it is TextView && it.text.toString().contains(text) }) } }
    private fun fill(id: Int,text: String) { ui { app.findViewById<EditText>(id).setText(text) } }
    fun testRotationKeepsTimedWorkout() {
        val context=instrumentation.targetContext
        context.getSharedPreferences("yourfit_local_v2",0).edit().clear().commit()
        val store=FitnessStore(context); store.register("Rotation","rotation@example.test","secret123")
        val session=Session.start("core"); session.running=true; session.tick(4000); store.session(session)
        app=activity; SystemClock.sleep(1000); click("Продолжить тренировку"); contains("00:26")
        click("Продолжить"); SystemClock.sleep(1300)
        val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
        ui { app.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        val rotated=monitor.waitForActivityWithTimeout(5000)
        assertNotNull("Activity did not recreate on rotation",rotated); app=rotated as MainActivity
        instrumentation.waitForIdleSync(); contains("Планка на прямых руках")
        val saved=FitnessStore(context).session()!!
        assertTrue(saved.remainingMs in 1L..24999L); assertFalse(saved.running)
        assertEquals("core",saved.workout); assertEquals(0,saved.exercise)
        ui { app.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        val portrait=monitor.waitForActivityWithTimeout(5000); if(portrait!=null) app=portrait as MainActivity
        instrumentation.removeMonitor(monitor)
    }
    fun testFullLocalJourney() {
        val context=instrumentation.targetContext
        // QA application has a different package; no user account is touched.
        context.getSharedPreferences("yourfit_local_v2",0).edit().clear().commit()
        app=activity; SystemClock.sleep(1000); click("Начать")
        click("Зарегистрироваться"); ui { assertNotNull(app.findViewById<EditText>(101).error) }
        fill(101,"Проверка"); fill(102,"qa@example.test"); fill(103,"secret123"); fill(104,"bad123")
        click("Зарегистрироваться"); ui { assertNotNull(app.findViewById<EditText>(104).error) }
        fill(104,"secret123"); click("Зарегистрироваться"); SystemClock.sleep(1300); contains("Привет, Проверка!")
        click("Профиль"); click("Мои данные  ›"); fill(201,"Иван"); fill(202,"72.5"); click("Сохранить"); contains("72.5 кг")
        click("Мои цели  ›"); ui { app.findViewById<CheckBox>(300).isChecked=true }; click("Сохранить цели"); contains("Стать сильнее")
        click("Тренировки"); click("Улица"); contains("Турник с нуля"); ui { assertFalse(views().any { it is TextView && it.text.toString()=="Ноги дома" }) }
        click("Зал"); contains("Жим лёжа"); click("Дом"); click("Ноги дома"); click("Начать тренировку")
        contains("Подход 1 из 3"); click("Начать"); SystemClock.sleep(1600); click("Пауза")
        val paused=FitnessStore(context).session()!!; assertTrue(paused.activeMs>=1000); assertFalse(paused.running)
        click("Сохранить и выйти"); contains("Продолжить тренировку"); click("Продолжить тренировку"); contains("Подход 1 из 3")
        click("Продолжить"); SystemClock.sleep(1200); click("Подход готов"); contains("Время отдохнуть")
        click("Пауза"); val before=FitnessStore(context).session()!!.remainingMs; click("+30 сек"); val after=FitnessStore(context).session()!!
        assertTrue(after.remainingMs-before>=29900); assertFalse(after.running)
        click("Пропустить отдых"); contains("Подход 2 из 3"); click("Начать"); SystemClock.sleep(1400); click("Подход готов")
        click("Пропустить отдых"); contains("Подход 3 из 3"); click("Начать"); SystemClock.sleep(1400); click("Подход готов")
        SystemClock.sleep(300)
        // Dialog lives in a separate window; inspect saved result, then use Back to dismiss.
        val saved=FitnessStore(context); assertNull(saved.session()); assertEquals(1,saved.account!!.getJSONArray("history").length())
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); instrumentation.waitForIdleSync()
        click("Статистика"); contains("1 из 7"); click("Месяц"); contains("1 из 30")
        click("Профиль"); click("Напоминания  ›"); ui { app.findViewById<CheckBox>(400).isChecked=true }; click("Сохранить напоминания"); contains("Пн · 19:00")
        click("Помощь тренера  ›"); click("Задать вопрос тренеру  ›"); fill(501,"Как подобрать нагрузку?"); click("Сохранить вопрос")
        assertEquals("Как подобрать нагрузку?",FitnessStore(context).account!!.getString("notes"))
    }
}
