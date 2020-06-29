package com.mapbox.navigation.core.reroute

import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.base.common.logger.Logger
import com.mapbox.base.common.logger.model.Message
import com.mapbox.base.common.logger.model.Tag
import com.mapbox.navigation.core.directions.session.DirectionsSession
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.routeoptions.RouteOptionsProvider
import com.mapbox.navigation.core.trip.session.TripSession

/**
 * Default implementation of [RerouteController]
 */
internal class MapboxRerouteController(
    private val directionsSession: DirectionsSession,
    private val tripSession: TripSession,
    private val routeOptionsProvider: RouteOptionsProvider,
    private val logger: Logger
) : RerouteController {

    private val observers = mutableSetOf<RerouteController.RerouteStateObserver>()

    override var state: RerouteState = RerouteState.Idle
        set(value) {
            if (field == value) {
                return
            }
            field = value
            observers.forEach { it.onNewState(field) }
        }

    private companion object {
        const val TAG = "MapboxRerouteController"
    }

    // current implementation ignore `onNewRoutes` callback because `DirectionsSession` update routes internally
    override fun reroute(routesCallback: RerouteController.RoutesCallback) {
        state = RerouteState.FetchingRoute
        logger.d(
            Tag(TAG),
            Message("Reroute has been started")
        )
        routeOptionsProvider.update(
            directionsSession.getRouteOptions(),
            tripSession.getRouteProgress(),
            tripSession.getEnhancedLocation()
        )
            ?.let { routeOptions ->
                tryReroute(routeOptions)
            }
            ?: kotlin.run {
                state = RerouteState.Failed("Cannot combine route options")
                state = RerouteState.Idle
            }
    }

    private fun tryReroute(routeOptions: RouteOptions) {
        directionsSession.requestRoutes(routeOptions, object : RoutesRequestCallback {
            // ignore result, DirectionsSession sets routes internally
            override fun onRoutesReady(routes: List<DirectionsRoute>) {
                logger.d(
                    Tag(TAG),
                    Message("Route request has been finished success.")
                )
                state = RerouteState.RouteHasBeenFetched
                state = RerouteState.Idle
            }

            override fun onRoutesRequestFailure(
                throwable: Throwable,
                routeOptions: RouteOptions
            ) {
                logger.e(
                    Tag(TAG),
                    Message("Route request has failed"),
                    throwable
                )

                state = RerouteState.Failed("Route request has failed", throwable)
                state = RerouteState.Idle
            }

            override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
                logger.d(
                    Tag(TAG),
                    Message("Route request has been canceled")
                )
                state = RerouteState.Interrupted
                state = RerouteState.Idle
            }
        })
    }

    override fun interrupt() {
        if (state == RerouteState.FetchingRoute) {
            directionsSession.cancel() // do not change state here because it's changed into onRoutesRequestCanceled callback
            logger.d(
                Tag(TAG),
                Message("Route fetching has been interrupted")
            )
        }
    }

    override fun addRerouteStateObserver(rerouteStateObserver: RerouteController.RerouteStateObserver): Boolean {
        rerouteStateObserver.onNewState(state)
        return observers.add(rerouteStateObserver)
    }

    override fun removeRerouteStateObserver(rerouteStateObserver: RerouteController.RerouteStateObserver): Boolean {
        return observers.remove(rerouteStateObserver)
    }
}
