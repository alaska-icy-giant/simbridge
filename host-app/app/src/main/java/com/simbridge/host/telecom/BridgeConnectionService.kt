package com.simbridge.host.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

/**
 * Android ConnectionService for managing call audio through SimBridge.
 * Registered as a PhoneAccount so the system routes call audio through us.
 */
class BridgeConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "BridgeConnService"
        private const val PRESENTATION_ALLOWED = 1
        var activeConnection: BridgeConnection? = null
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "onCreateOutgoingConnection: ${request?.address}")
        val connection = BridgeConnection().apply {
            setInitializing()
            setAddress(request?.address, PRESENTATION_ALLOWED)
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
        }
        activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "onCreateIncomingConnection")
        val connection = BridgeConnection().apply {
            setRinging()
            setAddress(request?.address, PRESENTATION_ALLOWED)
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
        }
        activeConnection = connection
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateOutgoingConnectionFailed")
        activeConnection = null
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateIncomingConnectionFailed")
        activeConnection = null
    }
}
