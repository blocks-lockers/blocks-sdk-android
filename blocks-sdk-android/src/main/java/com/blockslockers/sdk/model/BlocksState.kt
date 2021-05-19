package com.blockslockers.sdk.model

import kotlinx.serialization.*
import java.util.*

/**
 * Created by Alex Studnička on 12/05/2021.
 * Copyright © 2021 Property Blocks s.r.o. All rights reserved.
 */

@Serializable
enum class BlocksStateEnum {
    @SerialName("unknown") UNKNOWN,
    @SerialName("ready") READY,
    @SerialName("waiting_for_close") WAITING_FOR_CLOSE,
    @SerialName("finished") FINISHED,
    @SerialName("error") ERROR
}

@Serializable
data class BlocksState(
    val version: Int,
    val serialNo: String,
    val state: BlocksStateEnum,
    val error: String? = null,
    val packageId: String? = null
)
