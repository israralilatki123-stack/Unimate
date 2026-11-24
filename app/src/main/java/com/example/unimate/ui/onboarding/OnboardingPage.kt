package com.example.unimate.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.unimate.R

data class OnboardingPage(
    @StringRes val badgeRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val illustrationRes: Int,
    @StringRes val illustrationContentDescRes: Int,
)

val uniMateOnboardingPages = listOf(
    OnboardingPage(
        badgeRes = R.string.onboarding_step_1_badge,
        titleRes = R.string.onboarding_step_1_title,
        subtitleRes = R.string.onboarding_step_1_subtitle,
        illustrationRes = R.drawable.img_onboarding_campus,
        illustrationContentDescRes = R.string.cd_onboarding_1,
    ),
    OnboardingPage(
        badgeRes = R.string.onboarding_step_2_badge,
        titleRes = R.string.onboarding_step_2_title,
        subtitleRes = R.string.onboarding_step_2_subtitle,
        illustrationRes = R.drawable.img_onboarding_deadlines,
        illustrationContentDescRes = R.string.cd_onboarding_2,
    ),
    OnboardingPage(
        badgeRes = R.string.onboarding_step_3_badge,
        titleRes = R.string.onboarding_step_3_title,
        subtitleRes = R.string.onboarding_step_3_subtitle,
        illustrationRes = R.drawable.img_onboarding_grow,
        illustrationContentDescRes = R.string.cd_onboarding_3,
    ),
)
