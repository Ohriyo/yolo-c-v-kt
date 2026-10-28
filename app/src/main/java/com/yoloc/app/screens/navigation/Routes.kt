package com.yoloc.app.screens.navigation

import kotlinx.serialization.Serializable

/*
 * RN: the Expo Router file routes in app/. One type per screen; arguments are fields, read by
 * name from SavedStateHandle in the ViewModels ("id", "saved", "topic").
 */

/** RN: the (onboarding) group, shown until onboarding is done. */
@Serializable data object OnboardingGraph

@Serializable data object Welcome

@Serializable data object Permissions

/** Redesign 2026-10-28 (Kotlin only): the optional name, after the primer. */
@Serializable data object OnboardingName

/** RN: (tabs) and every screen pushed over them. */
@Serializable data object MainGraph

@Serializable data object Home

@Serializable data object History

@Serializable data object Learn

@Serializable data object Settings

@Serializable data object Camera

@Serializable data object Live

@Serializable data object Preview

@Serializable data object Processing

@Serializable data object Result

/** RN: /history/[id]; `saved` is the `?saved=1` that shows the "Saved" toast. */
@Serializable data class ScanDetail(val id: String, val saved: Boolean = false)

/** RN: /learn/[topic]. */
@Serializable data class LearnTopic(val topic: String)

@Serializable data object About

@Serializable data object Plots

/** RN: the app/dev screens. Only in debug builds. */
@Serializable data object DevGallery

@Serializable data object DevSeed

@Serializable data object DevInference
