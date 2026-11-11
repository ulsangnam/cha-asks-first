package ai.hackerton.sdkprobe

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.listener.OnServerConnectionListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import ai.pleos.playground.llm.constant.Mode as LlmMode
import ai.pleos.playground.llm.listener.ResultListener as LlmResultListener
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * Pleos SDK 동작(차량 신호, 내비, TTS/STT, LLM)을 에뮬레이터에서 확인하는 일회성 프로브.
 * adb shell am start -n ai.hackerton.sdkprobe/.MainActivity --es t <perm|vehicle|navi|tts|stt|llm>
 * 결과는 logcat의 PROBE 태그로만 출력한다.
 */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra("t") ?: "perm"
        log("start target=$target")
        guarded(target) {
            when (target) {
                "perm" -> probePermissions()
                "vehicle" -> probeVehicle()
                "navi" -> probeNavi()
                "tts" -> probeTts()
                "stt" -> probeStt()
                "llm" -> probeLlm()
                "llmserver" -> probeLlmServer()
                else -> log("unknown target")
            }
        }
    }

    private fun probePermissions() {
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.forEach { log("perm $it granted=${checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED}") }
    }

    private fun probeVehicle() {
        val vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        main.postDelayed({
            guarded("vehicle") {
                vehicle.getTurnSignal().checkTurnSignalStateCapabilities(cap("TurnSignal"), fail("TurnSignal"))
                vehicle.getBrake().checkBrakePedalStatusCapabilities(cap("Brake"), fail("Brake"))
                vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities({ c, _ -> cap("Steering")(c) }, fail("Steering"))
                vehicle.getDrivingMode().checkDrivingModeStateCapabilities(cap("DrivingMode"), fail("DrivingMode"))
                vehicle.getDrivingMode().checkCruiseControlStateCapabilities(cap("Cruise"), fail("Cruise"))
                vehicle.getSafety().checkTelltalesCapabilities(cap("Telltales"), fail("Telltales"))
                vehicle.getFuel().checkFuelPercentCapabilities(cap("FuelPercent"), fail("FuelPercent"))
                vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(cap("EvSoc"), fail("EvSoc"))
                vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(cap("Odometer"), fail("Odometer"))

                vehicle.getGear().getCurrentGearSelectionState({ log("value Gear=$it") }, fail("GearGet"))
                vehicle.getTurnSignal().getCurrentTurnSignalState({ log("value TurnSignal=$it") }, fail("TurnSignalGet"))
                vehicle.getSteeringWheel().getCurrentSteeringWheelAngle({ log("value Steering=$it") }, fail("SteeringGet"))
                vehicle.getBrake().getCurrentBrakePedalStatus({ log("value Brake=$it") }, fail("BrakeGet"))
                vehicle.getFuel().getFuelPercent({ log("value FuelPercent=$it") }, fail("FuelGet"))
                vehicle.getOdometer().getCurrentDistanceDriven({ log("value Odometer=$it") }, fail("OdometerGet"))

                vehicle.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                    override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = log("event TurnSignal=$value")
                    override fun onFailed(e: Exception) = log("event TurnSignal failed: $e")
                })
                vehicle.getGear().registerGearSelection(object : GearSelectionListener {
                    override fun onSelected(value: VehicleGear?) = log("event Gear=$value")
                    override fun onFailed(e: Exception) = log("event Gear failed: $e")
                })
                vehicle.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
                    override fun onBrakePedalStatusUpdated(value: Boolean?) = log("event Brake=$value")
                    override fun onFailed(e: Exception) = log("event Brake failed: $e")
                })
                vehicle.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
                    override fun onSteeringWheelAngleUpdated(value: Float?) = log("event Steering=$value")
                    override fun onFailed(e: Exception) = log("event Steering failed: $e")
                })
                log("vehicle listeners registered")
            }
        }, 2500)
    }

    private fun probeNavi() {
        val navi = NaviHelper(applicationContext)
        navi.initialize({
            log("navi init ok")
            navi.addListener(object : NaviHelperEventListener {
                override fun onRouteStateInfo(info: RouteStateInfo) = log("navi route state=$info")
                override fun onDestinationInfo(info: DestinationInfo) = log("navi destination=$info")
                override fun onDrivingInfo(info: DrivingInfo) = log("navi driving=$info")
                override fun onNavigationStatus(status: NavigationStatus) = log("navi status=$status")
                override fun onError(code: NaviErrorCode) = log("navi error=$code")
            })
            navi.getRouteStateInfo()
            navi.getDestinationInfo()
        }, { log("navi init failed: $it") })
    }

    private fun probeTts() {
        val started = SystemClock.elapsedRealtime()
        val tts = TextToSpeech(applicationContext, TtsMode.ON_DEVICE)
        tts.initialize(object : TtsInitListener {
            override fun onSuccess() {
                log("tts init ok in ${SystemClock.elapsedRealtime() - started}ms")
                tts.addEventListener(object : TtsEventListener {
                    override fun onStart() = log("tts onStart")
                    override fun onReady() = log("tts onReady")
                    override fun onDone() = log("tts onDone at ${SystemClock.elapsedRealtime() - started}ms")
                    override fun onError(error: TtsError) = log("tts onError=$error")
                    override fun onStop() = log("tts onStop")
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                tts.setLocale(Locale.KOREA)
                tts.speak("안녕하세요. 차가 먼저 묻는다 프로브입니다.", false)
            }

            override fun onFailure(error: TtsError) = log("tts init failed=$error")
        })
    }

    private fun probeStt() {
        val stt = SpeechToText(applicationContext, SttMode.ON_DEVICE)
        stt.initialize(object : SttInitListener {
            override fun onSuccess() {
                log("stt init ok")
                stt.addListener(object : SttResultListener {
                    override fun onUpdated(text: String, completed: Boolean) = log("stt text='$text' completed=$completed")
                    override fun onUpdatedEpdData(start: Long, end: Long) = log("stt epd $start-$end")
                    override fun onStartedRecognition() = log("stt started")
                    override fun onEndedRecognition() = log("stt ended")
                    override fun onError(error: SttError) = log("stt onError=$error")
                    override fun onReady() = log("stt onReady")
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                stt.setLocale(Locale.KOREA)
                stt.request()
                main.postDelayed({ log("stt stop"); stt.stop() }, 10_000)
            }

            override fun onFailure(error: SttError) = log("stt init failed=$error")
        })
    }

    private fun probeLlm() {
        val llm = LLM(applicationContext, LlmMode.ON_DEVICE)
        val began = SystemClock.elapsedRealtime()
        llm.initialize()
        // 서비스 연결 뒤 메인 스레드가 막히는지 재기 위해 별도 스레드에서 호출하고 생존 신호를 남긴다.
        Thread {
            for (second in 5..300 step 5) {
                main.post { log("llm main alive at ${SystemClock.elapsedRealtime() - began}ms") }
                Thread.sleep(5000)
                if (second == 5) requestLlm(llm)
                log("llm worker tick ${second}s")
            }
        }.start()
    }

    private fun probeLlmServer() {
        if (BuildConfig.PLEOS_CLIENT_ID.isEmpty() || BuildConfig.PLEOS_CLIENT_SECRET.isEmpty()) {
            log("llm server skipped: credentials missing in local.properties")
            return
        }
        val llm = LLM(applicationContext, LlmMode.SERVER)
        llm.initialize()
        // initialize에 완료 콜백이 없어 서비스 연결 시간을 두고 등록한다.
        Thread {
            Thread.sleep(3000)
            guarded("llm server") {
                log("llm registerApp")
                llm.registerApp(
                    BuildConfig.PLEOS_CLIENT_ID,
                    BuildConfig.PLEOS_CLIENT_SECRET,
                    object : OnServerConnectionListener {
                        override fun onConnected() {
                            log("llm server connected")
                            Thread { requestLlm(llm) }.start()
                        }

                        override fun onFailed(message: String) = log("llm server failed: $message")
                    },
                )
            }
        }.start()
    }

    private fun requestLlm(llm: LLM) {
        guarded("llm") {
            val started = SystemClock.elapsedRealtime()
            val prompt = intent.getStringExtra("p") ?: "운전자에게 건넬 인사말을 한 문장으로 말해줘."
            log("llm generateContent prompt=$prompt")
            llm.generateContent(prompt, object : LlmResultListener {
                override fun onResponse(text: String, completed: Boolean) =
                    log("llm response at ${SystemClock.elapsedRealtime() - started}ms completed=$completed text=$text")

                override fun onError(message: String) =
                    log("llm error at ${SystemClock.elapsedRealtime() - started}ms: $message")
            })
            log("llm generateContent returned after ${SystemClock.elapsedRealtime() - started}ms")
        }
    }

    private fun cap(name: String): (CapabilityConfig) -> Unit = { log("capability $name supported access=${it.accessType}") }

    private fun fail(name: String): (Exception) -> Unit = { log("capability $name FAILED ${it.javaClass.simpleName}: ${it.message}") }

    private fun guarded(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            log("$name threw ${e.javaClass.name}: ${e.message}")
        }
    }

    private fun log(message: String) {
        Log.i("PROBE", message)
    }
}
