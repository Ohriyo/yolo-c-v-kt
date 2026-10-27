package com.yoloc.app.features.scans

import android.content.res.Resources
import com.yoloc.app.R
import com.yoloc.app.features.detection.Verdict

/**
 * RN: verdictText.ts. The verdict banner sentence with counts, e.g. "Early-stage signs detected in
 * 2 of 5 pods". `countAtVerdictSeverity` is how many pods have the verdict's class.
 */
fun verdictTitle(res: Resources, verdict: Verdict, totalPods: Int, countAtVerdictSeverity: Int): String =
    when (verdict) {
        Verdict.Infected -> res.getQuantityString(R.plurals.result_verdict_infected, totalPods, countAtVerdictSeverity, totalPods)
        Verdict.EarlyStage -> res.getQuantityString(R.plurals.result_verdict_early, totalPods, countAtVerdictSeverity, totalPods)
        Verdict.Healthy -> res.getQuantityString(R.plurals.result_verdict_healthy, totalPods, totalPods)
        Verdict.NoPod -> res.getString(R.string.verdict_no_pod_title)
    }
