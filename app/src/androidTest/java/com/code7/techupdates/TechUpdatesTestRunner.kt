package com.code7.techupdates

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

/**
 * Instrumented test runner for the Tech Updates app.
 * Uses a singleton Application instance to avoid recreating the DI container.
 */
@RunWith(AndroidJUnit4::class)
class TechUpdatesTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TechUpdatesApp::class.java.name, context)
}
