package com.vosk

import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.FileInputStream
import java.io.IOException
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

@ReactModule(name = VoskModule.NAME)
class VoskModule(reactContext: ReactApplicationContext) :
        NativeVoskSpec(reactContext), RecognitionListener {

  private var model: Model? = null
  private var speechService: SpeechService? = null
  private var context: ReactApplicationContext? = reactContext
  private var recognizer: Recognizer? = null
  private var streamingRecognizer: Recognizer? = null
  private var sampleRate = 16000.0f
  private var isStopping = false

  override fun getName(): String {
    return NAME
  }

  override fun onResult(hypothesis: String) {
    // Get text data from string object
    val text = parseHypothesis(hypothesis)

    // Send event if data found
    if (!text.isNullOrEmpty()) {
      emitOnResult(text)
    }
  }

  override fun onFinalResult(hypothesis: String) {
    // Get text data from string object
    val text = parseHypothesis(hypothesis)

    // Send event if data found
    if (!text.isNullOrEmpty()) {
      emitOnFinalResult(text)
    }
  }

  override fun onPartialResult(hypothesis: String) {
    // Get text data from string object
    val text = parseHypothesis(hypothesis, "partial")

    // Send event if data found
    if (!text.isNullOrEmpty()) {
      emitOnPartialResult(text)
    }
  }

  override fun onError(e: Exception) {
    emitOnError(e.toString())
  }

  override fun onTimeout() {
    cleanRecognizer()
    emitOnTimeout()
  }

  /**
   * Converts hypothesis json text to the recognized text
   * @return the recognized text or null if something went wrong
   */
  private fun parseHypothesis(hypothesis: String, key: String = "text"): String? {
    // Hypothesis is in the form: '{[key]: "recognized text"}'
    try {
      val res = JSONObject(hypothesis)
      return res.getString(key)
    } catch (tx: Throwable) {
      return null
    }
  }

  /** Sends event to react native with associated data */
  private fun sendEvent(eventName: String, data: String? = null) {
    // Send event
    context?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, data)
  }

  /**
   * Translates array of string(s) to required kaldi string format
   * @return the array of string(s) as a single string
   */
  private fun makeGrammar(grammarArray: ReadableArray): String {
    return grammarArray
            .toArrayList()
            .joinToString(
                    prefix = "[",
                    separator = ", ",
                    transform = { "\"" + it + "\"" },
                    postfix = "]"
            )
  }

  override fun loadModel(path: String, promise: Promise) {
    cleanModel()
    try {
      this.model = Model(path)
      promise.resolve("Model successfully loaded")
    } catch (e: IOException) {
      println("Model directory does not exist at path: " + path)

      // Load model from main app bundle
      StorageService.unpack(
              context,
              path,
              "models",
              { model: Model? ->
                this.model = model
                promise.resolve("Model successfully loaded")
              }
      ) { e: IOException ->
        this.model = null
        promise.reject(e)
      }
    }
  }

  override fun start(options: ReadableMap?, promise: Promise) {
    if (model == null) {
      promise.reject(IOException("Model is not loaded yet"))
      return
    }
    if (speechService != null) {
      promise.reject(IOException("Recognizer is already in use"))
      return
    }
    try {
      recognizer =
              if (options != null && options.hasKey("grammar") && !options.isNull("grammar")) {
                Recognizer(model, sampleRate, makeGrammar(options.getArray("grammar")!!))
              } else {
                Recognizer(model, sampleRate)
              }
      speechService = SpeechService(recognizer, sampleRate)
      val started =
              if (options != null && options.hasKey("timeout") && !options.isNull("timeout")) {
                speechService?.startListening(this, options.getInt("timeout")) ?: false
              } else {
                speechService!!.startListening(this)
              }
      if (started) {
        promise.resolve("Recognizer successfully started")
      } else {
        cleanRecognizer()
        promise.reject(IOException("Recognizer couldn't be started"))
      }
    } catch (e: IOException) {
      cleanRecognizer()
      promise.reject(e)
    }
  }

  /**
   * Transcribe a 16 kHz mono WAV file from disk.
   * @param wavPath absolute path to a .wav file (PCM 16-bit LE, 16 kHz, mono)
   * @param promise resolves to the Vosk JSON string
   */
  @ReactMethod
  override fun transcribeFile(wavPath: String, promise: Promise) {
    // Ensure model is loaded
    if (model == null) {
      promise.reject("NO_MODEL", "Call loadModel() first")
      return
    }

    var recognizer: Recognizer? = null
    var input: FileInputStream? = null
    try {
      // Create a new recognizer on the same model & sample rate
      recognizer = Recognizer(model, sampleRate)

      // Open the WAV file
      input = FileInputStream(wavPath)
      val buffer = ByteArray(4096)
      var bytesRead: Int

      // Read & feed each chunk
      while (input.read(buffer).also { bytesRead = it } > 0) {
        recognizer.acceptWaveForm(buffer, bytesRead)
      }

      // Pull out the final result JSON
      val resultJson = recognizer.finalResult
      promise.resolve(resultJson)
    } catch (e: Exception) {
      promise.reject("TRANSCRIBE_FAIL", e)
    } finally {
      // Clean up
      try {
        input?.close()
      } catch (_: IOException) {}
      recognizer?.close()
    }
  }

  /**
   * Transcribe PCM 16-bit LE, 16 kHz, mono data from a Base64 string.
   * @param data Base64 encoded PCM data
   * @param promise resolves to the Vosk JSON string
   */
  @ReactMethod
  override fun transcribeData(data: String, promise: Promise) {
    // Ensure model is loaded
    if (model == null) {
      promise.reject("NO_MODEL", "Call loadModel() first")
      return
    }

    var recognizer: Recognizer? = null
    try {
      // Create a new recognizer on the same model & sample rate
      recognizer = Recognizer(model, sampleRate)

      // Decode Base64 string to byte array
      val bytes = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)

      // Feed the data to the recognizer
      recognizer.acceptWaveForm(bytes, bytes.size)

      // Pull out the final result JSON
      val resultJson = recognizer.finalResult
      promise.resolve(resultJson)
    } catch (e: Exception) {
      promise.reject("TRANSCRIBE_FAIL", e)
    } finally {
      // Clean up
      recognizer?.close()
    }
  }

  /**
   * Transcribe PCM 16-bit LE, 16 kHz, mono data from a byte array (passed as ReadableArray).
   * @param data ReadableArray of bytes (numbers)
   * @param promise resolves to the Vosk JSON string
   */
  @ReactMethod
  override fun transcribeDataArray(data: ReadableArray, promise: Promise) {
    // Ensure model is loaded
    if (model == null) {
      promise.reject("NO_MODEL", "Call loadModel() first")
      return
    }

    var recognizer: Recognizer? = null
    try {
      // Create a new recognizer on the same model & sample rate
      recognizer = Recognizer(model, sampleRate)

      // Convert ReadableArray to ByteArray
      val size = data.size()
      val bytes = ByteArray(size)
      for (i in 0 until size) {
        bytes[i] = data.getInt(i).toByte()
      }

      // Feed the data to the recognizer
      recognizer.acceptWaveForm(bytes, size)

      // Pull out the final result JSON
      val resultJson = recognizer.finalResult
      promise.resolve(resultJson)
    } catch (e: Exception) {
      promise.reject("TRANSCRIBE_FAIL", e)
    } finally {
      // Clean up
      recognizer?.close()
    }
  }

  /**
   * Start a streaming session for progressive PCM data transcription.
   * @param options Optional settings (grammar)
   * @param promise resolves when streaming session is initialized
   */
  @ReactMethod
  override fun startStreaming(options: ReadableMap?, promise: Promise) {
    if (model == null) {
      promise.reject("NO_MODEL", "Call loadModel() first")
      return
    }
    if (streamingRecognizer != null) {
      promise.reject("ALREADY_STREAMING", "Streaming already active")
      return
    }

    try {
      streamingRecognizer =
              if (options != null && options.hasKey("grammar") && !options.isNull("grammar")) {
                Recognizer(model, sampleRate, makeGrammar(options.getArray("grammar")!!))
              } else {
                Recognizer(model, sampleRate)
              }
      promise.resolve("Streaming started")
    } catch (e: Exception) {
      promise.reject("START_STREAMING_FAIL", e)
    }
  }

  /**
   * Feed a chunk of PCM data to the streaming session. Emits partial results via onPartialResult
   * and onResult events.
   * @param data ReadableArray of PCM bytes (16-bit LE, 16 kHz, mono)
   * @param promise resolves to true after chunk is processed
   */
  @ReactMethod
  override fun feedChunk(data: ReadableArray, promise: Promise) {
    if (streamingRecognizer == null) {
      promise.reject("NO_RECOGNIZER", "Call startStreaming() first")
      return
    }

    try {
      // Convert ReadableArray to ByteArray
      val size = data.size()
      val bytes = ByteArray(size)
      for (i in 0 until size) {
        bytes[i] = data.getInt(i).toByte()
      }

      // Feed chunk to recognizer
      if (streamingRecognizer!!.acceptWaveForm(bytes, size)) {
        // Got a complete result
        val result = streamingRecognizer!!.result
        val text = parseHypothesis(result)
        if (!text.isNullOrEmpty()) {
          emitOnResult(text)
        }
      }

      // Always emit partial result
      val partial = streamingRecognizer!!.partialResult
      val partialText = parseHypothesis(partial, "partial")
      if (!partialText.isNullOrEmpty()) {
        emitOnPartialResult(partialText)
      }

      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("FEED_CHUNK_FAIL", e)
    }
  }

  /**
   * Stop the streaming session and get the final result.
   * @param promise resolves to the final Vosk JSON result
   */
  @ReactMethod
  override fun stopStreaming(promise: Promise) {
    if (streamingRecognizer == null) {
      promise.reject("NO_RECOGNIZER", "No active streaming session")
      return
    }

    try {
      val finalResult = streamingRecognizer!!.finalResult
      streamingRecognizer!!.close()
      streamingRecognizer = null
      promise.resolve(finalResult)
    } catch (e: Exception) {
      streamingRecognizer?.close()
      streamingRecognizer = null
      promise.reject("STOP_STREAMING_FAIL", e)
    }
  }

  private fun cleanRecognizer() {
    synchronized(this) {
      if (isStopping) {
        return
      }
      isStopping = true
      try {
        speechService?.let {
          it.stop()
          it.shutdown()
          speechService = null
        }
        recognizer?.let {
          it.close()
          recognizer = null
        }
      } catch (e: Exception) {
        Log.w(NAME, "Error during cleanup in cleanRecognizer", e)
      } finally {
        isStopping = false
      }
    }
  }

  private fun cleanModel() {
    synchronized(this) {
      try {
        model?.let {
          it.close()
          model = null
        }
      } catch (e: Exception) {
        Log.w(NAME, "Error during model cleanup", e)
      }
    }
  }

  override fun stop() {
    cleanRecognizer()
  }

  override fun unload() {
    cleanRecognizer()
    cleanModel()
  }

  override fun addListener(type: String?) {
    // Keep: Required for RN built in Event Emitter Calls.
  }

  override fun removeListeners(count: Double): Unit {
    // Keep: Required for RN built in Event Emitter Calls.
  }

  companion object {
    const val NAME = "Vosk"
  }
}
