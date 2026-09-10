/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-17.
 */

package pl.szczodrzynski.edziennik.ext

import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.LoginMethod
import pl.szczodrzynski.edziennik.data.enums.LoginMode
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.data.enums.NotificationType
import pl.szczodrzynski.edziennik.data.enums.Theme

fun Int.asFeatureType() = FeatureType.values().first { it.id == this }
fun Int.asLoginMethod() = LoginMethod.values().first { it.id == this }
fun Int.asLoginMode() = LoginMode.values().first { it.id == this }
fun Int.asLoginType() = LoginType.values().first { it.id == this }
fun Int.asMetadataType() = MetadataType.values().first { it.id == this }
fun Int.asNotificationType() = NotificationType.values().first { it.id == this }
fun Int.asNavTarget() = NavTarget.values().first { it.id == this }

fun Int?.asFeatureTypeOrNull() = FeatureType.values().firstOrNull { it.id == this }
fun Int?.asLoginMethodOrNull() = LoginMethod.values().firstOrNull { it.id == this }
fun Int?.asLoginModeOrNull() = LoginMode.values().firstOrNull { it.id == this }
fun Int?.asLoginTypeOrNull() = LoginType.values().firstOrNull { it.id == this }
fun Int?.asMetadataTypeOrNull() = MetadataType.values().firstOrNull { it.id == this }
fun Int?.asNotificationTypeOrNull() = NotificationType.values().firstOrNull { it.id == this }
fun Int?.asNavTargetOrNull() = NavTarget.values().firstOrNull { it.id == this }

fun Enum<*>.toInt() = when (this) {
    is FeatureType -> this.id
    is LoginMethod -> this.id
    is LoginMode -> this.id
    is LoginType -> this.id
    is MetadataType -> this.id
    is NotificationType -> this.id
    is NavTarget -> this.id
    else -> this.ordinal
}

inline fun <reified E : Enum<E>> Int.toEnum() = when (E::class.java) {
    // enums commented out are not really used in Bundles
    FeatureType::class.java -> this.asFeatureType()
    // LoginMethod::class.java -> this.asLoginMethod()
    LoginMode::class.java -> this.asLoginMode()
    LoginType::class.java -> this.asLoginType()
    // MetadataType::class.java -> this.asMetadataType()
    // NotificationType::class.java -> this.asNotificationType()
    NavTarget::class.java -> this.asNavTarget()
    else -> enumValues<E>()[this]
} as E

fun <E : Enum<E>> Int.toEnum(type: Class<*>): Enum<E> = when (type) {
    // this is used for Config so all enums are here
    FeatureType::class.java -> this.asFeatureType()
    LoginMethod::class.java -> this.asLoginMethod()
    LoginMode::class.java -> this.asLoginMode()
    LoginType::class.java -> this.asLoginType()
    MetadataType::class.java -> this.asMetadataType()
    NotificationType::class.java -> this.asNotificationType()
    NavTarget::class.java -> this.asNavTarget()
    else -> throw IllegalArgumentException("Unknown type $type")
} as E

fun <E : Enum<E>> String.toEnum(type: Class<*>) = when (type) {
    FeatureType::class.java -> enumValueOf<FeatureType>(this)
    LoginMethod::class.java -> enumValueOf<LoginMethod>(this)
    LoginMode::class.java -> enumValueOf<LoginMode>(this)
    LoginType::class.java -> enumValueOf<LoginType>(this)
    MetadataType::class.java -> enumValueOf<MetadataType>(this)
    NavTarget::class.java -> enumValueOf<NavTarget>(this)
    NotificationType::class.java -> enumValueOf<NotificationType>(this)
    Theme::class.java -> enumValueOf<Theme>(this)
    Theme.Type::class.java -> enumValueOf<Theme.Type>(this)
    Theme.Mode::class.java -> enumValueOf<Theme.Mode>(this)
    else -> throw IllegalArgumentException("Unknown type $type")
} as E

fun <E : Enum<E>> String.toEnumOrNull(type: Class<*>): E? =
    try {
        this.toEnum<E>(type)
    } catch (e: Exception) {
        null
    }

inline fun <reified E : Enum<E>> String.toEnum(): E =
    this.toEnum(E::class.java)

inline fun <reified E : Enum<E>> String.toEnumOrNull(): E? =
    this.toEnumOrNull<E>(E::class.java)
