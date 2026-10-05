package ru.natk.yourfit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.*
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    private val bg=Color.rgb(247,247,247)
    private val ink=Color.rgb(21,21,21)
    private val muted=Color.rgb(110,110,115)
    private val soft=Color.rgb(238,238,239)
    private val green=Color.rgb(120,184,108)
    private val font by lazy { ResourcesCompat.getFont(this,R.font.inter_regular) }
    private val bold by lazy { ResourcesCompat.getFont(this,R.font.inter_semibold) }
    private val store by lazy { FitnessStore(this) }
    private lateinit var page: LinearLayout
    private lateinit var body: LinearLayout
    private var screen="splash"
    private var selected="full"
    private var filter="Все"
    private var period="Неделя"
    private var session: Session?=null
    private val handler=Handler(Looper.getMainLooper())
    private var lastTick=0L
    private var checkpoint=0L
    private var clockLabel: TextView?=null
    private var toggleButton: TextView?=null
    private var doneButton: TextView?=null
    private var progress: ProgressBar?=null
    private var ring: TimerRing?=null
    private var repInput: EditText?=null
    private var busy=false
    private var completion=false
    private val stack=arrayListOf<String>()
    private val tabs=listOf("home","trainings","stats","profile")
    private val ticker=object: Runnable {
        override fun run() {
            advanceClock()
            session?.let { s ->
                if(s.rest && s.remainingMs==0L) { s.prepare(); store.session(s); render() } else updateTimer()
                if(SystemClock.elapsedRealtime()-checkpoint>1500) { store.session(s); checkpoint=SystemClock.elapsedRealtime() }
            }
            handler.postDelayed(this,100)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=true; isAppearanceLightNavigationBars=true }
        window.statusBarColor=Color.TRANSPARENT; window.navigationBarColor=bg
        session=store.session()
        savedInstanceState?.let {
            screen=it.getString("screen","home"); selected=it.getString("selected","full")
            filter=it.getString("filter","Все"); period=it.getString("period","Неделя")
            stack.addAll(it.getStringArrayList("stack")?:arrayListOf()); completion=it.getBoolean("completion")
        }
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } })
        render(); if(completion) showCompletion()
    }
    override fun onResume() { super.onResume(); lastTick=SystemClock.elapsedRealtime(); handler.post(ticker) }
    override fun onPause() { advanceClock(); session?.running=false; store.session(session); handler.removeCallbacks(ticker); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        advanceClock(); session?.running=false; store.session(session)
        out.putString("screen",screen); out.putString("selected",selected); out.putString("filter",filter); out.putString("period",period)
        out.putStringArrayList("stack",stack); out.putBoolean("completion",completion); super.onSaveInstanceState(out)
    }
    private fun advanceClock() { val now=SystemClock.elapsedRealtime(); if(lastTick>0) session?.tick(now-lastTick); lastTick=now }
    private fun dp(n: Int)=(n*resources.displayMetrics.density).roundToInt()
    private fun shape(color: Int,radius: Int=18)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun touch(v: View,color: Int=Color.WHITE,radius: Int=18,action: ()->Unit) {
        v.background=RippleDrawable(ColorStateList.valueOf(0x22000000),shape(color,radius),null)
        v.isFocusable=true; v.setOnClickListener { action() }
    }
    private fun txt(value: String,size: Float=16f,color: Int=ink,heavy: Boolean=false)=TextView(this).apply {
        text=value; textSize=size; setTextColor(color); typeface=if(heavy) bold else font
        includeFontPadding=false; setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun gap(n: Int=16) { body.addView(View(this),LinearLayout.LayoutParams(1,dp(n))) }
    private fun title(value: String,subtitle: String?=null) { body.addView(txt(value,30f,ink,true)); if(subtitle!=null) { gap(8); body.addView(txt(subtitle,14f,muted)) }; gap(24) }
    private fun heading(value: String) { gap(24); body.addView(txt(value,20f,ink,true)); gap(12) }
    private fun note(value: String) { body.addView(txt(value,14f,muted)); gap(12) }
    private fun button(value: String,secondary: Boolean=false,action: ()->Unit)=txt(value,15f,if(secondary) ink else Color.WHITE,true).apply {
        gravity=Gravity.CENTER; minHeight=dp(54); setPadding(dp(12),dp(14),dp(12),dp(14))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) }; touch(this,if(secondary) soft else ink,14,action)
    }
    private fun link(value: String,action: ()->Unit)=txt(value,14f,ink,true).apply { gravity=Gravity.CENTER; minHeight=dp(48); touch(this,bg,12,action) }
    private fun field(hintText: String,type: Int=InputType.TYPE_CLASS_TEXT,idValue: Int=View.NO_ID,initial: String="")=EditText(this).apply {
        id=idValue; hint=hintText; contentDescription=hintText; textSize=16f; typeface=font; inputType=type
        setTextColor(ink); setHintTextColor(muted); setSingleLine(true); setPadding(dp(16),dp(14),dp(16),dp(14))
        background=shape(Color.WHITE,14); minHeight=dp(56); setText(initial)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
    }
    private fun card()=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(16)); background=shape(Color.WHITE)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
    }
    private fun row(label: String,detail: String="",action: ()->Unit) {
        val box=card(); box.minimumHeight=dp(64); touch(box,Color.WHITE,18,action); box.addView(txt("$label  ›",16f,ink,true))
        if(detail.isNotEmpty()) box.addView(txt(detail,13f,muted).apply { setPadding(0,dp(7),0,0) }); body.addView(box)
    }
    private fun image(name: String,height: Int,description: String)=ImageView(this).apply {
        setImageResource(Catalog.images.getValue(name))
        scaleType=ImageView.ScaleType.CENTER_CROP; contentDescription=description; background=shape(soft); clipToOutline=true
        layoutParams=LinearLayout.LayoutParams(-1,dp(height))
    }
    private fun hideKeyboard() { currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken,0) }; currentFocus?.clearFocus() }
    private fun go(target: String,root: Boolean=false) {
        hideKeyboard(); if(screen=="session" && target!="session") pauseSession()
        if(root || target in tabs || target=="welcome") stack.clear() else if(target!=screen) stack.add(screen)
        screen=target; render()
    }
    private fun back() {
        hideKeyboard()
        if(screen=="session") { pauseSession(); go("home",true); return }
        if(stack.isNotEmpty()) { screen=stack.removeAt(stack.lastIndex); render(); return }
        if(screen in tabs && screen!="home") go("home",true)
        else if(screen in listOf("login","register")) go("welcome",true) else moveTaskToBack(true)
    }
    private fun render() {
        if(store.account==null && screen !in listOf("splash","welcome","register","login")) screen="welcome"
        if(screen=="session" && session==null) screen="home"
        clockLabel=null; toggleButton=null; doneButton=null; progress=null; ring=null; repInput=null
        page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg) }; setContentView(page)
        ViewCompat.setOnApplyWindowInsetsListener(page) { v,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime()); v.setPadding(bars.left,bars.top,bars.right,max(bars.bottom,ime.bottom)); insets
        }; ViewCompat.requestApplyInsets(page)
        if(screen=="splash") {
            page.gravity=Gravity.CENTER; val mark=txt("yourfit.",58f,ink,true); page.addView(mark); mark.alpha=0f; mark.animate().alpha(1f).setDuration(450).start()
            handler.postDelayed({ if(screen=="splash") go(if(store.account==null) "welcome" else "home",true) },650); return
        }
        val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(4),dp(16),dp(4)) }
        if(screen !in tabs && screen!="welcome") bar.addView(txt("‹",34f).apply { gravity=Gravity.CENTER; contentDescription="Назад"; touch(this,bg,12) { back() } },LinearLayout.LayoutParams(dp(48),dp(48)))
        bar.addView(txt("yourfit.",26f,ink,true).apply { gravity=Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(0,dp(48),1f))
        if(store.account!=null) bar.addView(txt("☰",23f).apply { gravity=Gravity.CENTER; contentDescription="Меню"; touch(this,bg,12) {
            AlertDialog.Builder(this@MainActivity).setTitle("yourfit").setItems(arrayOf("Расписание","Мои цели","Помощь тренера")) { _,which -> go(listOf("schedule","goals","help")[which]) }.show()
        } },LinearLayout.LayoutParams(dp(48),dp(48)))
        page.addView(bar)
        val scroll=ScrollView(this).apply { id=R.id.screen_scroll; clipToPadding=false }
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(16),dp(20),dp(24)) }
        scroll.addView(body,android.widget.FrameLayout.LayoutParams(min(resources.displayMetrics.widthPixels,dp(520)),-2,Gravity.CENTER_HORIZONTAL)); page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(screen) {
            "welcome","register","login" -> auth(); "home" -> home(); "trainings" -> trainings(); "program" -> program()
            "session" -> workoutSession(); "stats" -> statistics(); "profile" -> profile(); "edit" -> editProfile()
            "goals" -> goals(); "reminders" -> reminders(); "schedule" -> schedule(); "help" -> help(); "question" -> question()
        }
        if(screen in tabs) bottomNav()
        body.alpha=0f; body.animate().alpha(1f).setDuration(150).start()
    }
    private fun bottomNav() {
        val nav=LinearLayout(this).apply { setPadding(dp(4),dp(8),dp(4),dp(6)); setBackgroundColor(Color.WHITE) }
        val icons=listOf(R.mipmap.ic_home,R.drawable.ic_workouts,R.mipmap.ic_stats,R.mipmap.ic_profile)
        listOf("Главная","Тренировки","Статистика","Профиль").forEachIndexed { i,name ->
            val color=if(screen==tabs[i]) ink else Color.rgb(142,142,147)
            val item=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; contentDescription=name; isSelected=screen==tabs[i]; touch(this,Color.WHITE,12) { go(tabs[i],true) } }
            item.addView(ImageView(this).apply { setImageResource(icons[i]); imageTintList=ColorStateList.valueOf(color); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(23),dp(23)))
            item.addView(txt(name,10f,color,screen==tabs[i]).apply { gravity=Gravity.CENTER; setPadding(0,dp(5),0,0) }); nav.addView(item,LinearLayout.LayoutParams(0,dp(58),1f))
        }; page.addView(nav)
    }
    private fun auth() {
        if(screen=="welcome") {
            body.addView(image("home_pushups",260,"Домашняя тренировка")); gap(24); title("Сильнее\nс каждым днём","Тренировки для твоих целей")
            body.addView(button("Начать") { go("register") }); body.addView(link("Уже есть аккаунт? Войти") { go("login") }); return
        }
        val registration=screen=="register"
        title(if(registration) "Начни с себя" else "С возвращением","Аккаунт и результаты сохраняются на этом устройстве")
        val name=if(registration) field("Имя",idValue=101).also { body.addView(it) } else null
        val email=field("E-mail",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,102); body.addView(email)
        val password=field("Пароль",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,103); body.addView(password)
        val repeat=if(registration) field("Повтори пароль",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,104).also { body.addView(it) } else null
        note(if(registration) "Пароль: не менее 6 символов" else "Используй e-mail своего локального аккаунта")
        body.addView(button(if(registration) "Зарегистрироваться" else "Войти") {
            if(busy) return@button
            val e=email.text.toString().trim(); val p=password.text.toString()
            when {
                registration && name!!.text.toString().trim().length !in 1..40 -> name!!.error="Введи имя от 1 до 40 символов"
                !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> email.error="Проверь e-mail"
                p.length<6 -> password.error="Нужно не менее 6 символов"
                registration && p!=repeat!!.text.toString() -> repeat.error="Пароли не совпадают"
                else -> {
                    busy=true; hideKeyboard(); val n=name?.text?.toString().orEmpty()
                    Thread {
                        val result=runCatching { if(registration) store.register(n,e,p) else store.login(e,p) }
                        runOnUiThread {
                            busy=false; if(isDestroyed) return@runOnUiThread
                            if(result.getOrDefault(false)) { session=store.session(); go("home",true) }
                            else if(result.isFailure) toast("Не удалось сохранить аккаунт. Попробуй снова.")
                            else email.error=if(registration) "Этот e-mail уже зарегистрирован" else "Неверный e-mail или пароль"
                        }
                    }.start()
                }
            }
        })
        body.addView(link(if(registration) "Уже есть аккаунт? Войти" else "Нет аккаунта? Регистрация") { go(if(registration) "login" else "register") })
    }
    private fun home() {
        val a=store.account!!; title("Привет, ${a.getString("name")}!","Сегодня можно сделать ещё один шаг")
        session?.let { s ->
            val box=card(); touch(box,ink,18) { selected=s.workout; go("session") }
            box.addView(txt("Продолжить тренировку",13f,Color.LTGRAY)); box.addView(txt(s.plan.name,23f,Color.WHITE,true).apply { setPadding(0,dp(12),0,dp(8)) })
            box.addView(txt("${s.exercise+1} из ${s.plan.exercises.size} упражнений · подход ${s.set+1}",13f,Color.LTGRAY)); body.addView(box)
        } ?: run {
            val hero=card(); hero.addView(txt("Твой темп. Твой результат.",21f,ink,true)); hero.addView(txt("Начни с короткой тренировки дома",14f,muted).apply { setPadding(0,dp(8),0,dp(16)) })
            hero.addView(button("Выбрать тренировку") { filter="Дом"; go("trainings",true) }); body.addView(hero)
        }
        val upcoming=a.getJSONArray("schedule").objects().filter { it.getLong("date")>=dayStart(System.currentTimeMillis()) }.minByOrNull { it.getLong("date") }
        if(upcoming!=null) row("Ближайшая тренировка","${Catalog.get(upcoming.getString("workout")).name} · ${date(upcoming.getLong("date"))}") { go("schedule") }
        heading("Мои цели"); val goals=a.getJSONArray("goals").strings()
        row(if(goals.isEmpty()) "Выбрать цели" else goals.joinToString(" · "),"Меняй их в любой момент") { go("goals") }
        heading("Рекомендуем дома")
        val picks=if("Стать сильнее" in goals) listOf("push","full") else if("Набрать массу" in goals) listOf("full","legs") else listOf("core","full")
        picks.forEach { workoutCard(Catalog.get(it)) }
    }
    private fun chips(options: List<String>,active: String,action: (String)->Unit) {
        val line=LinearLayout(this)
        options.forEach { option ->
            val v=txt(option,13f,if(active==option) Color.WHITE else ink,active==option).apply { gravity=Gravity.CENTER; minHeight=dp(44); touch(this,if(active==option) ink else soft,22) { action(option) } }
            line.addView(v,LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(5) })
        }; body.addView(line); gap(20)
    }
    private fun trainings() { title("Тренировки","Дома, на улице или в зале"); chips(listOf("Все","Дом","Улица","Зал"),filter) { filter=it; render() }; Catalog.workouts.filter { filter=="Все" || it.category==filter }.forEach { workoutCard(it) } }
    private fun workoutCard(w: Workout) {
        val box=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(10),dp(10),dp(12),dp(10)); touch(this,Color.WHITE,18) { selected=w.id; go("program") } }
        box.addView(image(w.image,84,w.exercises.first().name),LinearLayout.LayoutParams(dp(84),dp(84)))
        val text=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,dp(5),0) }
        text.addView(txt(w.name,15f,ink,true)); text.addView(txt("≈ ${w.minutes} мин · ${w.level}",12f,muted).apply { setPadding(0,dp(8),0,0) })
        box.addView(text,LinearLayout.LayoutParams(0,-2,1f)); box.addView(txt("›",24f,muted)); body.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun program() {
        val w=Catalog.get(selected); body.addView(image(w.image,220,w.exercises.first().name)); gap(20); title(w.name,"≈ ${w.minutes} мин · ${w.level} · ${w.category}")
        note(w.equipment); body.addView(txt(w.description)); gap(16); note(w.muscles)
        if(w.id=="abs") note("Повторяй эту сессию в своём расписании в течение 30 дней. История поможет следить за регулярностью.")
        body.addView(button(if(session?.workout==w.id) "Продолжить тренировку" else "Начать тренировку") { startWorkout(w) })
        body.addView(button("Добавить в расписание",true) { chooseDate(w) }); heading("Что будем делать")
        w.exercises.forEachIndexed { i,e -> val c=card(); c.addView(txt("${i+1}. ${e.name}",16f,ink,true)); c.addView(txt(e.prescription,13f,muted).apply { setPadding(0,dp(8),0,0) }); body.addView(c) }
    }
    private fun startWorkout(w: Workout) {
        fun start() { session=Session.start(w.id); store.session(session); go("session") }
        when { session==null -> start(); session!!.workout==w.id -> go("session")
            else -> AlertDialog.Builder(this).setTitle("Уже есть тренировка").setMessage("Продолжить «${session!!.plan.name}» или начать новую? Незавершённые подходы не попадут в статистику.")
                .setPositiveButton("Продолжить") { _,_ -> go("session") }.setNegativeButton("Начать новую") { _,_ -> start() }.setNeutralButton("Отмена",null).show() }
    }
    private fun workoutSession() {
        val s=session?:return; val e=s.current
        if(s.rest) {
            title("Время отдохнуть",s.plan.name); val frame=FrameLayout(this)
            ring=TimerRing(this,green,soft).also { frame.addView(it,FrameLayout.LayoutParams(-1,-1)) }
            val center=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER }
            clockLabel=txt("",46f,ink,true).apply { gravity=Gravity.CENTER }.also { center.addView(it) }; center.addView(txt("Отдых",15f,muted).apply { gravity=Gravity.CENTER; setPadding(0,dp(10),0,0) })
            frame.addView(center,FrameLayout.LayoutParams(-1,-1)); body.addView(frame,LinearLayout.LayoutParams(dp(260),dp(260)).apply { gravity=Gravity.CENTER_HORIZONTAL }); gap(24)
            val next=card(); next.addView(txt("Далее",13f,muted)); next.addView(txt(e.name,21f,ink,true).apply { setPadding(0,dp(10),0,dp(8)) }); next.addView(txt("Подход ${s.set+1} из ${e.sets} · ${if(e.seconds>0) "${e.seconds} сек" else "${e.reps} повторений"}",14f,muted)); body.addView(next)
            toggleButton=button("Пауза") { toggleTimer() }; body.addView(toggleButton)
            body.addView(button("+30 сек",true) { advanceClock(); s.remainingMs+=30000; s.restTotal+=30000; store.session(s); updateTimer() })
            body.addView(link("Пропустить отдых") { s.prepare(); store.session(s); render() })
        } else {
            note("${s.plan.name} · ${s.exercise+1} из ${s.plan.exercises.size}"); body.addView(image(e.image,200,e.name)); gap(20)
            title(e.name,"Подход ${s.set+1} из ${e.sets} · ${if(e.seconds>0) "${e.seconds} секунд" else "цель ${e.reps} повторений"}")
            clockLabel=txt("",36f,ink,true).also { body.addView(it) }; gap(12)
            progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=1000; progressTintList=ColorStateList.valueOf(green); progressBackgroundTintList=ColorStateList.valueOf(soft) }
            body.addView(progress,LinearLayout.LayoutParams(-1,dp(6))); gap(16)
            if(e.seconds==0) { note("Секундомер считает время подхода. Укажи выполненные повторения."); repInput=field("Повторения",InputType.TYPE_CLASS_NUMBER,110,e.reps.toString()); body.addView(repInput) }
            toggleButton=button("Начать") { toggleTimer() }; body.addView(toggleButton)
            doneButton=button("Подход готов",true) { finishSet() }; body.addView(doneButton)
            heading("Техника"); e.tips.forEachIndexed { i,t -> note("${i+1}. $t") }
        }
        body.addView(link("Сохранить и выйти") { pauseSession(); go("home",true) }); updateTimer()
    }
    private fun pauseSession() { advanceClock(); session?.running=false; store.session(session) }
    private fun toggleTimer() { val s=session?:return; advanceClock(); if(!s.rest && s.current.seconds>0 && s.remainingMs==0L) return; s.running=!s.running; lastTick=SystemClock.elapsedRealtime(); store.session(s); updateTimer() }
    private fun updateTimer() {
        val s=session?:return
        clockLabel?.text=time(if(s.rest || s.current.seconds>0) (s.remainingMs+999)/1000 else s.setMs/1000)
        toggleButton?.text=if(s.running) "Пауза" else if(!s.rest && s.current.seconds>0 && s.remainingMs==0L) "Время вышло" else if(s.setMs>0 || s.rest) "Продолжить" else "Начать"
        toggleButton?.isEnabled=s.rest || s.current.seconds==0 || s.remainingMs>0; toggleButton?.alpha=if(toggleButton?.isEnabled==true) 1f else 0.5f
        val ready=!s.rest && s.setMs>=1000 && (s.current.seconds==0 || s.remainingMs==0L); doneButton?.isEnabled=ready; doneButton?.alpha=if(ready) 1f else 0.4f
        if(s.rest) ring?.fraction=(s.remainingMs.toFloat()/s.restTotal).coerceIn(0f,1f)
        else progress?.setProgress(if(s.current.seconds>0) (s.setMs*1000/(s.current.seconds*1000L)).toInt().coerceIn(0,1000) else (s.set*1000/s.current.sets),true)
    }
    private fun finishSet() {
        val s=session?:return; advanceClock(); if(s.rest || s.setMs<1000 || (s.current.seconds>0 && s.remainingMs>0)) return
        val reps=if(s.current.seconds>0) 0 else repInput?.text.toString().toIntOrNull()
        if(reps==null || (s.current.seconds==0 && reps !in 1..500)) { repInput?.error="Введи число от 1 до 500"; return }
        if(s.finishSet(reps)) { store.complete(s); session=null; completion=true; go("home",true); showCompletion() }
        else { store.session(s); lastTick=SystemClock.elapsedRealtime(); render() }
    }
    private fun showCompletion() { AlertDialog.Builder(this).setTitle("Тренировка завершена").setMessage("Отличная работа! Тренировка завершена.\nРезультат сохранён в статистике.").setPositiveButton("Готово") { _,_ -> completion=false; go("stats",true) }.setOnCancelListener { completion=false }.show() }
    private fun chooseDate(w: Workout) {
        val c=Calendar.getInstance()
        val picker=DatePickerDialog(this,{ _,y,m,d ->
            c.set(y,m,d)
            TimePickerDialog(this,{ _,h,min ->
                c.set(Calendar.HOUR_OF_DAY,h); c.set(Calendar.MINUTE,min); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0)
                if(c.timeInMillis<System.currentTimeMillis()) { toast("Выбери время в будущем"); return@TimePickerDialog }
                AlertDialog.Builder(this).setTitle("Добавить в расписание").setMessage("Добавить эту тренировку в расписание?\n${w.name}\n${date(c.timeInMillis)}")
                    .setPositiveButton("Добавить") { _,_ ->
                        val list=store.account!!.getJSONArray("schedule")
                        if(list.objects().none { it.getString("workout")==w.id && it.getLong("date")==c.timeInMillis }) { list.put(JSONObject().put("id",UUID.randomUUID().toString()).put("workout",w.id).put("date",c.timeInMillis)); store.save() }; go("schedule")
                    }.setNegativeButton("Отмена",null).show()
            },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show()
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH))
        picker.datePicker.minDate=dayStart(c.timeInMillis); picker.show()
    }
    private fun schedule() {
        title("Расписание","Планируй тренировки в удобные дни")
        val list=store.account!!.getJSONArray("schedule").objects().sortedBy { it.getLong("date") }
        if(list.isEmpty()) note("Пока ничего не запланировано. Добавь программу из каталога.")
        list.forEach { item ->
            val w=Catalog.get(item.getString("workout")); val c=card(); c.addView(txt(w.name,19f,ink,true)); c.addView(txt(date(item.getLong("date")),14f,muted).apply { setPadding(0,dp(8),0,dp(16)) })
            c.addView(button("Открыть программу") { selected=w.id; go("program") })
            c.addView(button("Убрать из расписания",true) { AlertDialog.Builder(this).setTitle("Убрать тренировку?").setMessage(w.name).setPositiveButton("Убрать") { _,_ ->
                val a=store.account!!; a.put("schedule",JSONArray(a.getJSONArray("schedule").objects().filter { it.getString("id")!=item.getString("id") })); store.save(); render()
            }.setNegativeButton("Отмена",null).show() }); body.addView(c)
        }; body.addView(button("Выбрать тренировку",true) { go("trainings",true) })
    }
    private fun statistics() {
        val a=store.account!!; val days=if(period=="Неделя") 7 else 30
        val calendar=Calendar.getInstance().apply { timeInMillis=dayStart(System.currentTimeMillis()); add(Calendar.DAY_OF_YEAR,-days+1) }
        val records=a.getJSONArray("history").objects().filter { it.getLong("date")>=calendar.timeInMillis }
        title("Статистика","Результат складывается из привычки"); chips(listOf("Неделя","Месяц"),period) { period=it; render() }
        val times=records.sumOf { it.getLong("activeMs") }; val kcal=records.sumOf { it.getDouble("calories") }; val active=records.map { dayStart(it.getLong("date")) }.distinct().size; val weight=a.optDouble("weight",0.0)
        listOf(listOf("Тренировки" to records.size.toString(),"Активное время" to duration(times)),listOf("Калории ≈" to "${kcal.roundToInt()} ккал","Вес" to if(weight>0) "${number(weight)} кг" else "Не указан")).forEach { values ->
            val row=LinearLayout(this); values.forEach { (key,value) -> val c=card(); c.addView(txt(key,12f,muted)); c.addView(txt(value,20f,ink,true).apply { setPadding(0,dp(10),0,0) }); row.addView(c,LinearLayout.LayoutParams(0,dp(90),1f).apply { marginEnd=dp(8); bottomMargin=dp(8) }) }; body.addView(row)
        }
        heading("Регулярность"); note("$active из $days дней с тренировкой")
        val values=ArrayList<Int>(); val labels=ArrayList<String>()
        repeat(days) { val stamp=calendar.timeInMillis; values.add(records.count { dayStart(it.getLong("date"))==stamp }); labels.add(SimpleDateFormat(if(days==7) "EE" else "d",Locale("ru")).format(Date(stamp))); calendar.add(Calendar.DAY_OF_YEAR,1) }
        body.addView(ActivityChart(this,values,labels,ink,green,muted),LinearLayout.LayoutParams(-1,dp(170)))
        gap(12); note("Число тренировок по дням · последние $days дней")
        if(records.isEmpty()) note("Пока нет завершённых тренировок за этот период. Начни с небольшой программы.")
        note(if(weight==0.0) "Укажи вес в профиле для расчёта калорий. Прошлые записи не пересчитываются." else "Калории — приблизительная оценка по весу и активному времени, без учёта отдыха.")
        heading("История тренировок")
        records.sortedByDescending { it.getLong("date") }.forEach { r -> row(Catalog.get(r.getString("workout")).name,"${date(r.getLong("date"))} · ${duration(r.getLong("activeMs"))}") {
            val sets=r.getJSONArray("sets").objects().joinToString("\n") { "${it.getString("exercise")}, подход ${it.getInt("set")}: ${if(it.getInt("reps")>0) "${it.getInt("reps")} повт." else time(it.getLong("ms")/1000)}" }
            AlertDialog.Builder(this).setTitle("Выполненные подходы").setMessage(sets).setPositiveButton("Понятно",null).show()
        } }
    }
    private fun profile() {
        val a=store.account!!; title("Профиль")
        body.addView(txt(a.getString("name").take(1).uppercase(),34f,ink,true).apply { gravity=Gravity.CENTER; background=shape(soft,40) },LinearLayout.LayoutParams(dp(80),dp(80))); gap(16)
        body.addView(txt(a.getString("name"),26f,ink,true)); gap(6); note(a.getString("email"))
        val history=a.getJSONArray("history").objects(); note("Тренировок: ${history.size} · Активно: ${duration(history.sumOf { it.getLong("activeMs") })}")
        row("Мои данные",if(a.optDouble("weight")>0) "Вес ${number(a.getDouble("weight"))} кг" else "Имя и вес") { go("edit") }
        row("Мои цели",a.getJSONArray("goals").strings().joinToString(" · ")) { go("goals") }; row("Расписание") { go("schedule") }
        row("Напоминания",reminderSummary()) { go("reminders") }
        row("Внешний вид","Светлая тема") { AlertDialog.Builder(this).setTitle("Светлая тема").setMessage("В приложении используется светлая тема yourfit: спокойные поверхности, чёрный текст и зелёный прогресс.").setPositiveButton("Понятно",null).show() }
        row("Помощь тренера") { go("help") }
        body.addView(button("Выйти",true) { AlertDialog.Builder(this).setTitle("Выйти из аккаунта?").setMessage("Результаты останутся на этом устройстве.").setPositiveButton("Выйти") { _,_ -> pauseSession(); store.logout(); session=null; go("welcome",true) }.setNegativeButton("Отмена",null).show() })
    }
    private fun editProfile() {
        val a=store.account!!; title("Мои данные"); note("Имя")
        val name=field("Имя",idValue=201,initial=a.getString("name")); body.addView(name); note("Вес, кг")
        val weight=field("Например, 72.5",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,202,if(a.optDouble("weight")>0) number(a.getDouble("weight")) else ""); body.addView(weight)
        body.addView(button("Сохранить") {
            val n=name.text.toString().trim(); val w=weight.text.toString().replace(',','.').toDoubleOrNull()
            if(n.length !in 1..40) { name.error="Имя: от 1 до 40 символов"; return@button }
            if(w==null || !w.isFinite() || w !in 20.0..350.0) { weight.error="Вес: от 20 до 350 кг"; return@button }
            a.put("name",n).put("weight",w); store.save(); back()
        })
    }
    private fun goals() {
        title("Мои цели","Выбери то, что важно сейчас"); val a=store.account!!; val selected=a.getJSONArray("goals").strings().toMutableSet()
        listOf("Стать сильнее","Набрать массу","Быть в форме").forEachIndexed { i,value -> body.addView(CheckBox(this).apply { id=300+i; text=value; textSize=16f; typeface=font; setTextColor(ink); minHeight=dp(60); isChecked=value in selected; buttonTintList=ColorStateList.valueOf(ink); setOnCheckedChangeListener { _,checked -> if(checked) selected.add(value) else selected.remove(value) } }) }
        gap(24); body.addView(button("Сохранить цели") { a.put("goals",JSONArray(selected.toList())); store.save(); back() })
    }
    private val weekDays=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
    private fun reminderSummary(): String { val a=store.account!!; val d=a.getJSONArray("reminderDays").strings(); return if(d.isEmpty()) "Дни не выбраны" else "${d.joinToString(", ")} · ${a.getString("reminderTime")}" }
    private fun reminders() {
        title("Напоминания","Твой привычный ритм занятий"); val a=store.account!!; val chosen=a.getJSONArray("reminderDays").strings().toMutableSet(); var time=a.getString("reminderTime")
        weekDays.forEachIndexed { i,d -> body.addView(CheckBox(this).apply { id=400+i; text=d; textSize=16f; typeface=font; setTextColor(ink); minHeight=dp(48); isChecked=d in chosen; buttonTintList=ColorStateList.valueOf(ink); setOnCheckedChangeListener { _,on -> if(on) chosen.add(d) else chosen.remove(d) } }) }
        val timeButton=button("Время: $time",true) {}; timeButton.setOnClickListener { val parts=time.split(':'); TimePickerDialog(this,{ _,h,m -> time=String.format(Locale.ROOT,"%02d:%02d",h,m); timeButton.text="Время: $time" },parts[0].toInt(),parts[1].toInt(),true).show() }; body.addView(timeButton)
        note("Эта настройка сохраняет дни и время в приложении. Системные уведомления не отправляются.")
        body.addView(button("Сохранить напоминания") { a.put("reminderDays",JSONArray(weekDays.filter { it in chosen })).put("reminderTime",time); store.save(); back() })
    }
    private fun help() {
        title("Помощь тренера","Всё для самостоятельных занятий")
        row("Управление расписанием","Запланированные тренировки") { go("schedule") }; row("Планы тренировок","Выбрать подходящую программу") { go("trainings",true) }
        row("Контроль прогресса","История, активное время и регулярность") { go("stats",true) }; row("Задать вопрос тренеру","Записать вопрос для личной консультации") { go("question") }
    }
    private fun question() {
        title("Вопрос тренеру","Сохрани заметку, чтобы обсудить её на консультации"); val a=store.account!!
        val input=field("Что хочешь уточнить?",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,501,a.optString("notes")); input.setSingleLine(false); input.minLines=5; input.gravity=Gravity.TOP; body.addView(input)
        note("Заметка хранится только здесь и никому не отправляется."); body.addView(button("Сохранить вопрос") { a.put("notes",input.text.toString().trim()); store.save(); hideKeyboard(); toast("Вопрос сохранён") })
    }
    private fun toast(s: String) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show() }
    private fun time(seconds: Long)=String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60)
    private fun duration(ms: Long): String=if(ms<60000) "${ms/1000} сек" else if(ms<3600000) "${ms/60000} мин" else "${ms/3600000} ч ${(ms/60000)%60} мин"
    private fun number(d: Double)=if(d==d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.ROOT,"%.1f",d)
    private fun date(ms: Long)=SimpleDateFormat("d MMM, HH:mm",Locale("ru")).format(Date(ms))
    private fun dayStart(ms: Long)=Calendar.getInstance().apply { timeInMillis=ms; set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
}

private class TimerRing(context: android.content.Context,val accent: Int,val track: Int): View(context) {
    var fraction=1f; set(value) { field=value; invalidate() }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
    private val bounds=RectF()
    override fun onDraw(c: Canvas) { val pad=width*0.045f; paint.strokeWidth=width*0.04f; bounds.set(pad,pad,width-pad,height-pad); paint.color=track; c.drawOval(bounds,paint); paint.color=accent; c.drawArc(bounds,-90f,360*fraction,false,paint) }
}
private class ActivityChart(context: android.content.Context,val values: List<Int>,val labels: List<String>,val ink: Int,val green: Int,val muted: Int): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription="Тренировок по дням: "+values.joinToString(", "); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(c: Canvas) {
        val density=resources.displayMetrics.density; val baseline=height-30*density; val step=width.toFloat()/values.size; val maxValue=max(1,values.maxOrNull()?:1)
        values.forEachIndexed { i,v ->
            paint.color=if(i==values.lastIndex) green else ink; val left=i*step+step*0.18f; val right=(i+1)*step-step*0.18f; val h=(baseline-24*density)*v/maxValue
            if(v>0) c.drawRoundRect(left,baseline-h,right,baseline,3*density,3*density,paint) else { paint.color=0xffdddde0.toInt(); c.drawRoundRect(left,baseline-2*density,right,baseline,2*density,2*density,paint) }
            paint.textAlign=Paint.Align.CENTER; paint.textSize=11*density; paint.color=muted
            if(values.size==7 || i%5==0 || i==values.lastIndex) c.drawText(labels[i],i*step+step/2,height-6*density,paint)
            if(v>0 && values.size==7) c.drawText(v.toString(),i*step+step/2,baseline-h-6*density,paint)
        }
    }
}
