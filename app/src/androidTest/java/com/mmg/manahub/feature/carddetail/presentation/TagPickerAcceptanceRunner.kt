package com.mmg.manahub.feature.carddetail.presentation

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.test.runner.AndroidJUnitRunner

/** Runs the standalone picker fixture without production initialization or account data. */
class TagPickerAcceptanceRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(Process.myUid() / 100000 > 0)
        return super.newApplication(cl, Application::class.java.name, context)
    }

    override fun onCreate(arguments: Bundle) {
        check(arguments.getString("isolatedTagPickerFixture") == "true")
        check(arguments.getString("class") == TagPickerScrollAcceptanceTest::class.java.name)
        val user = arguments.getString("fixtureUser")?.toIntOrNull()
        check(user != null && user > 0 && Process.myUid() / 100000 == user)
        super.onCreate(arguments)
    }
}
