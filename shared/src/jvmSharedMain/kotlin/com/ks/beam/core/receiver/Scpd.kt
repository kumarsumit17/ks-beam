package com.ks.beam.core.receiver

/** Service descriptions (SCPD) for the actions the receiver answers. Some senders read them first. */
internal object Scpd {
    private fun scpd(actions: List<Pair<String, List<Triple<String, String, String>>>>, variables: List<Pair<String, String>>) = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">")
        append("<specVersion><major>1</major><minor>0</minor></specVersion><actionList>")
        for ((name, args) in actions) {
            append("<action><name>$name</name><argumentList>")
            for ((arg, dir, variable) in args) {
                append("<argument><name>$arg</name><direction>$dir</direction><relatedStateVariable>$variable</relatedStateVariable></argument>")
            }
            append("</argumentList></action>")
        }
        append("</actionList><serviceStateTable>")
        for ((name, type) in variables) {
            append("<stateVariable sendEvents=\"${if (name == "LastChange") "yes" else "no"}\"><name>$name</name><dataType>$type</dataType></stateVariable>")
        }
        append("</serviceStateTable></scpd>")
    }

    private fun i(name: String, v: String) = Triple(name, "in", v)
    private fun o(name: String, v: String) = Triple(name, "out", v)
    private val id = i("InstanceID", "A_ARG_TYPE_InstanceID")

    val AV_TRANSPORT = scpd(
        listOf(
            "SetAVTransportURI" to listOf(id, i("CurrentURI", "AVTransportURI"), i("CurrentURIMetaData", "AVTransportURIMetaData")),
            "Play" to listOf(id, i("Speed", "TransportPlaySpeed")),
            "Pause" to listOf(id),
            "Stop" to listOf(id),
            "Seek" to listOf(id, i("Unit", "A_ARG_TYPE_SeekMode"), i("Target", "A_ARG_TYPE_SeekTarget")),
            "GetPositionInfo" to listOf(
                id, o("Track", "CurrentTrack"), o("TrackDuration", "CurrentTrackDuration"),
                o("TrackMetaData", "CurrentTrackMetaData"), o("TrackURI", "CurrentTrackURI"),
                o("RelTime", "RelativeTimePosition"), o("AbsTime", "AbsoluteTimePosition"),
                o("RelCount", "RelativeCounterPosition"), o("AbsCount", "AbsoluteCounterPosition"),
            ),
            "GetTransportInfo" to listOf(
                id, o("CurrentTransportState", "TransportState"), o("CurrentTransportStatus", "TransportStatus"),
                o("CurrentSpeed", "TransportPlaySpeed"),
            ),
            "GetMediaInfo" to listOf(
                id, o("NrTracks", "NumberOfTracks"), o("MediaDuration", "CurrentMediaDuration"),
                o("CurrentURI", "AVTransportURI"), o("CurrentURIMetaData", "AVTransportURIMetaData"),
                o("NextURI", "NextAVTransportURI"), o("NextURIMetaData", "NextAVTransportURIMetaData"),
                o("PlayMedium", "PlaybackStorageMedium"), o("RecordMedium", "RecordStorageMedium"),
                o("WriteStatus", "RecordMediumWriteStatus"),
            ),
            "GetTransportSettings" to listOf(id, o("PlayMode", "CurrentPlayMode"), o("RecQualityMode", "CurrentRecordQualityMode")),
            "GetDeviceCapabilities" to listOf(
                id, o("PlayMedia", "PossiblePlaybackStorageMedia"), o("RecMedia", "PossibleRecordStorageMedia"),
                o("RecQualityModes", "PossibleRecordQualityModes"),
            ),
            "GetCurrentTransportActions" to listOf(id, o("Actions", "CurrentTransportActions")),
        ),
        listOf(
            "A_ARG_TYPE_InstanceID" to "ui4", "AVTransportURI" to "string", "AVTransportURIMetaData" to "string",
            "NextAVTransportURI" to "string", "NextAVTransportURIMetaData" to "string",
            "TransportPlaySpeed" to "string", "A_ARG_TYPE_SeekMode" to "string", "A_ARG_TYPE_SeekTarget" to "string",
            "CurrentTrack" to "ui4", "CurrentTrackDuration" to "string", "CurrentTrackMetaData" to "string",
            "CurrentTrackURI" to "string", "RelativeTimePosition" to "string", "AbsoluteTimePosition" to "string",
            "RelativeCounterPosition" to "i4", "AbsoluteCounterPosition" to "i4", "TransportState" to "string",
            "TransportStatus" to "string", "NumberOfTracks" to "ui4", "CurrentMediaDuration" to "string",
            "PlaybackStorageMedium" to "string", "RecordStorageMedium" to "string", "RecordMediumWriteStatus" to "string",
            "CurrentPlayMode" to "string", "CurrentRecordQualityMode" to "string", "PossiblePlaybackStorageMedia" to "string",
            "PossibleRecordStorageMedia" to "string", "PossibleRecordQualityModes" to "string",
            "CurrentTransportActions" to "string", "LastChange" to "string",
        ),
    )

    val CONNECTION_MANAGER = scpd(
        listOf(
            "GetProtocolInfo" to listOf(o("Source", "SourceProtocolInfo"), o("Sink", "SinkProtocolInfo")),
            "GetCurrentConnectionIDs" to listOf(o("ConnectionIDs", "CurrentConnectionIDs")),
            "GetCurrentConnectionInfo" to listOf(
                i("ConnectionID", "A_ARG_TYPE_ConnectionID"), o("RcsID", "A_ARG_TYPE_RcsID"),
                o("AVTransportID", "A_ARG_TYPE_AVTransportID"), o("ProtocolInfo", "A_ARG_TYPE_ProtocolInfo"),
                o("PeerConnectionManager", "A_ARG_TYPE_ConnectionManager"), o("PeerConnectionID", "A_ARG_TYPE_ConnectionID"),
                o("Direction", "A_ARG_TYPE_Direction"), o("Status", "A_ARG_TYPE_ConnectionStatus"),
            ),
        ),
        listOf(
            "SourceProtocolInfo" to "string", "SinkProtocolInfo" to "string", "CurrentConnectionIDs" to "string",
            "A_ARG_TYPE_ConnectionID" to "i4", "A_ARG_TYPE_RcsID" to "i4", "A_ARG_TYPE_AVTransportID" to "i4",
            "A_ARG_TYPE_ProtocolInfo" to "string", "A_ARG_TYPE_ConnectionManager" to "string",
            "A_ARG_TYPE_Direction" to "string", "A_ARG_TYPE_ConnectionStatus" to "string",
        ),
    )

    val RENDERING_CONTROL = scpd(
        listOf(
            "GetVolume" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), o("CurrentVolume", "Volume")),
            "SetVolume" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), i("DesiredVolume", "Volume")),
            "GetMute" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), o("CurrentMute", "Mute")),
            "SetMute" to listOf(id, i("Channel", "A_ARG_TYPE_Channel"), i("DesiredMute", "Mute")),
        ),
        listOf(
            "A_ARG_TYPE_InstanceID" to "ui4", "A_ARG_TYPE_Channel" to "string", "Volume" to "ui2",
            "Mute" to "boolean", "LastChange" to "string",
        ),
    )
}
