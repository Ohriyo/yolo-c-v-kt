package com.yoloc.app.features.learn

import androidx.annotation.StringRes
import com.yoloc.app.R
import com.yoloc.app.components.IconName

/** RN: strings.learn.photoTips. Photography tips only (not agronomic advice), so they are final copy. */
data class PhotoTip(val icon: IconName, @param:StringRes val text: Int)

val photoTips = listOf(
    PhotoTip(IconName.Maximize, R.string.learn_photo_tip_fill),
    PhotoTip(IconName.Sun, R.string.learn_photo_tip_shade),
    PhotoTip(IconName.Crop, R.string.learn_photo_tip_whole),
    PhotoTip(IconName.Move, R.string.learn_photo_tip_closer),
    PhotoTip(IconName.PauseCircle, R.string.learn_photo_tip_still),
    PhotoTip(IconName.Droplet, R.string.learn_photo_tip_lens),
    PhotoTip(IconName.RotateCw, R.string.learn_photo_tip_other_side),
)

/** RN: strings.onboarding.tipPicks. The four photo tips that matter most for a first photo. */
val onboardingTipPicks = listOf(0, 1, 2, 3)

/** RN: strings.learn.howSteps. */
data class HowStep(val icon: IconName, @param:StringRes val title: Int, @param:StringRes val body: Int)

val howSteps = listOf(
    HowStep(IconName.Camera, R.string.learn_how_photo_title, R.string.learn_how_photo_body),
    HowStep(IconName.Cpu, R.string.learn_how_phone_title, R.string.learn_how_phone_body),
    HowStep(IconName.Tag, R.string.learn_how_label_title, R.string.learn_how_label_body),
    HowStep(IconName.Flag, R.string.learn_how_verdict_title, R.string.learn_how_verdict_body),
    HowStep(IconName.Edit3, R.string.learn_how_correct_title, R.string.learn_how_correct_body),
)

/** RN: topicIcon. */
val LearnTopic.iconName: IconName get() = IconName.fromKey(icon)

/** RN: strings.learn.topics[topic].title. */
@get:StringRes
val LearnTopic.title: Int
    get() = when (this) {
        LearnTopic.Stages -> R.string.learn_topic_stages_title
        LearnTopic.Photos -> R.string.learn_topic_photos_title
        LearnTopic.How -> R.string.learn_topic_how_title
    }

/** RN: strings.learn.topics[topic].teaser. */
@get:StringRes
val LearnTopic.teaser: Int
    get() = when (this) {
        LearnTopic.Stages -> R.string.learn_topic_stages_teaser
        LearnTopic.Photos -> R.string.learn_topic_photos_teaser
        LearnTopic.How -> R.string.learn_topic_how_teaser
    }
