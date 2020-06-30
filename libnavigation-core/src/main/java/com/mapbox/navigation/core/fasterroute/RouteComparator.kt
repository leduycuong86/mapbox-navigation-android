package com.mapbox.navigation.core.fasterroute

import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.base.trip.model.RouteProgress

internal class RouteComparator {
    /**
     * TODO https://github.com/mapbox/mapbox-navigation-android/issues/3144
     */
    fun isNewRoute(routeProgress: RouteProgress, alternativeRoute: DirectionsRoute): Boolean {
        return true
    }
}
