package com.adaptiveui.animeapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt-annotated so dependency graph is generated.
 */
@HiltAndroidApp
class AnimeApp : Application()
