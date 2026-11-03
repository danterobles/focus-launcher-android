package com.focuslauncher.data.local

import com.focuslauncher.data.local.proto.AttemptStateProto
import com.focuslauncher.data.local.proto.FocusStateProto
import com.focuslauncher.data.local.proto.PinConfigProto
import com.focuslauncher.domain.model.AppPackageKey
import com.focuslauncher.domain.model.LauncherSettings
import com.focuslauncher.domain.model.UserSerial
import com.focuslauncher.domain.model.VisibilityPolicy

object StateMapper {

    /** Estado v1 de una instalación limpia: bienvenida pendiente, sin reglas, PIN deshabilitado. */
    fun initialState(): FocusStateProto = FocusStateProto.newBuilder()
        .setSchemaVersion(StateValidator.SCHEMA_VERSION)
        .setOnboardingAccepted(false)
        .setPin(PinConfigProto.newBuilder().setDisabled(true))
        .setAttempts(AttemptStateProto.getDefaultInstance())
        .build()

    /** Proyección para la UI: sin verificador ni contador. Las claves duplicadas se normalizan como conjunto. */
    fun toSettings(state: FocusStateProto): LauncherSettings = LauncherSettings(
        onboardingAccepted = state.onboardingAccepted,
        visibility = VisibilityPolicy(
            state.hiddenPackagesList.mapTo(LinkedHashSet()) { AppPackageKey(it.packageName, UserSerial(it.userSerial)) },
        ),
        pinEnabled = state.pin.modeCase == PinConfigProto.ModeCase.ENABLED,
    )
}
