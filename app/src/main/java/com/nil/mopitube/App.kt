package com.nil.mopitube

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.nil.mopitube.mopidy.MopidyClient

class App : Application() {
	// Expose a single MopidyClient for the app process
	lateinit var mopidyClient: MopidyClient

	override fun onCreate() {
		super.onCreate()
		mopidyClient = MopidyClient(this)

		// Observe the process lifecycle and start the websocket when the app
		// moves to the foreground. This helps ensure the connection is active
		// when users interact with the app.
		// Keep the Mopidy connection alive even when the app is backgrounded.
		// This ensures playback/control remains available while backgrounded.
		ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_START -> mopidyClient.start()
				// Intentionally do not stop on ON_STOP; keep WS alive in background.
				else -> {}
			}
		})
	}
}