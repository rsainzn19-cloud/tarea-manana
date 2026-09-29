package com.mangatraductor.core

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.File

/** Cómo se ejecuta un modelo de imagen (detector, LaMa). */
enum class Acceleration {
    /** El motor de CPU de ONNX Runtime (MLAS), con varios hilos. */
    CPU,

    /** XNNPACK, el acelerador de ONNX Runtime para móviles (sólo en onnxruntime-android). */
    XNNPACK,
}

internal object Sessions {

    /**
     * Abre [model] con [acceleration]. Si XNNPACK no está en esta versión de
     * ONNX Runtime (p. ej. en el PC), se abre con la CPU.
     */
    fun open(model: File, threads: Int, acceleration: Acceleration): Pair<OrtSession, Acceleration> {
        val env = OrtEnvironment.getEnvironment()
        if (acceleration == Acceleration.XNNPACK) {
            try {
                val options = OrtSession.SessionOptions().apply {
                    // XNNPACK usa sus propios hilos: ONNX Runtime, uno y sin esperas activas.
                    setIntraOpNumThreads(1)
                    addConfigEntry("session.intra_op.allow_spinning", "0")
                    addConfigEntry("session.set_denormal_as_zero", "1")
                    addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                }
                return env.createSession(model.path, options) to Acceleration.XNNPACK
            } catch (e: OrtException) {
                // sin XNNPACK: se sigue con la CPU
            }
        }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            // Sin esto estas redes pasan por números "desnormales" y van ~20 veces más lentas.
            addConfigEntry("session.set_denormal_as_zero", "1")
        }
        return env.createSession(model.path, options) to Acceleration.CPU
    }

    /**
     * Abre el modelo de las dos maneras, mide cuánto tarda cada una con
     * [run] (la segunda ejecución, ya caliente) y se queda con la más rápida.
     */
    fun <T : AutoCloseable> fastest(
        make: (Acceleration) -> T,
        accelerationOf: (T) -> Acceleration,
        run: (T) -> Unit,
    ): T {
        val accelerated = make(Acceleration.XNNPACK)
        if (accelerationOf(accelerated) != Acceleration.XNNPACK) return accelerated
        val fast = try {
            timed(accelerated, run)
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
        val cpu = make(Acceleration.CPU)
        val normal = timed(cpu, run)
        return if (fast < normal) {
            cpu.close()
            accelerated
        } else {
            accelerated.close()
            cpu
        }
    }

    private fun <T> timed(model: T, run: (T) -> Unit): Long {
        run(model) // la primera vez prepara la memoria
        val start = System.nanoTime()
        run(model)
        return System.nanoTime() - start
    }
}
