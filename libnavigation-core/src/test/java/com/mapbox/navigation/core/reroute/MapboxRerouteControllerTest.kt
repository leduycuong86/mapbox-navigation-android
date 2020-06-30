package com.mapbox.navigation.core.reroute

import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.base.common.logger.Logger
import com.mapbox.navigation.core.directions.session.DirectionsSession
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.routeoptions.RouteOptionsProvider
import com.mapbox.navigation.core.trip.session.TripSession
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapboxRerouteControllerTest {

    private lateinit var rerouteController: MapboxRerouteController

    @MockK
    private lateinit var directionsSession: DirectionsSession

    @MockK
    private lateinit var tripSession: TripSession

    @MockK
    private lateinit var routeOptionsProvider: RouteOptionsProvider

    @MockK
    private lateinit var logger: Logger

    @MockK
    private lateinit var routeOptions: RouteOptions

    @MockK
    private lateinit var routeCallback: RerouteController.RoutesCallback

    @MockK
    lateinit var primaryRerouteObserver: RerouteController.RerouteStateObserver

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true, relaxed = true)
        rerouteController = spyk(
            MapboxRerouteController(
                directionsSession,
                tripSession,
                routeOptionsProvider,
                logger
            )
        )
    }

    @After
    fun cleanUp() {
        assertEquals(RerouteState.Idle, rerouteController.state)
        // routeCallback mustn't called in current implementation. DirectionSession update routes internally
        verify(exactly = 0) { routeCallback.onNewRoutes(any()) }
    }

    @Test
    fun initial_state() {
        assertEquals(rerouteController.state, RerouteState.Idle)
        verify(exactly = 0) { rerouteController.reroute(any()) }
        verify(exactly = 0) { rerouteController.interrupt() }
    }

    @Test
    fun initial_state_with_added_state_observer() {
        val added = addRerouteStateObserver()

        assertTrue("RerouteStateObserver is not added", added)
        verify(exactly = 1) { primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle) }
    }

    @Test
    fun reroute_success() {
        mockNewRouteOptions()
        addRerouteStateObserver()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(
                routeOptions,
                capture(routeRequestCallback)
            )
        } returns mockk()

        rerouteController.reroute(routeCallback)
        routeRequestCallback.captured.onRoutesReady(mockk())

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
        }
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.RouteFetched)
        }
        verify(exactly = 2) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
        verifyOrder {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.RouteFetched)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
    }

    @Test
    fun reroute_unsuccess() {
        mockNewRouteOptions()
        addRerouteStateObserver()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(routeOptions, capture(routeRequestCallback))
        } returns mockk()

        rerouteController.reroute(routeCallback)
        routeRequestCallback.captured.onRoutesRequestFailure(mockk(), mockk())

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
        }
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(ofType<RerouteState.Failed>())
        }
        verify(exactly = 2) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
        verifyOrder {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
            primaryRerouteObserver.onRerouteStateChanged(ofType<RerouteState.Failed>())
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
    }

    @Test
    fun reroute_request_canceled_external() {
        mockNewRouteOptions()
        addRerouteStateObserver()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(routeOptions, capture(routeRequestCallback))
        } returns mockk()

        rerouteController.reroute(routeCallback)
        routeRequestCallback.captured.onRoutesRequestCanceled(mockk())

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
        }
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Interrupted)
        }
        verify(exactly = 2) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
        verifyOrder {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Interrupted)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
    }

    @Test
    fun interrupt_route_request() {
        mockNewRouteOptions()
        addRerouteStateObserver()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(routeOptions, capture(routeRequestCallback))
        } returns mockk()
        every {
            directionsSession.cancel()
        } answers {
            routeRequestCallback.captured.onRoutesRequestCanceled(mockk())
        }

        rerouteController.reroute(routeCallback)
        rerouteController.interrupt()

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
        }
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Interrupted)
        }
        verify(exactly = 2) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
        verifyOrder {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Interrupted)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
    }

    @Test
    fun invalid_route_option() {
        mockNewRouteOptions(null)
        addRerouteStateObserver()

        rerouteController.reroute(routeCallback)

        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
        }
        verify(exactly = 1) {
            primaryRerouteObserver.onRerouteStateChanged(ofType<RerouteState.Failed>())
        }
        verify(exactly = 2) {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
        verifyOrder {
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.FetchingRoute)
            primaryRerouteObserver.onRerouteStateChanged(ofType<RerouteState.Failed>())
            primaryRerouteObserver.onRerouteStateChanged(RerouteState.Idle)
        }
        verify(exactly = 0) { directionsSession.requestRoutes(any(), any()) }
    }

    @Test
    fun add_the_same_observer_twice_and_remove_twice() {
        mockNewRouteOptions()

        assertTrue(addRerouteStateObserver())
        assertFalse(addRerouteStateObserver())

        assertTrue(rerouteController.unregisterRerouteStateObserver(primaryRerouteObserver))
        assertFalse(rerouteController.unregisterRerouteStateObserver(primaryRerouteObserver))
    }

    private fun addRerouteStateObserver(rerouteStateObserver: RerouteController.RerouteStateObserver = primaryRerouteObserver): Boolean {
        return rerouteController.registerRerouteStateObserver(rerouteStateObserver)
    }

    private fun mockNewRouteOptions(_routeOptions: RouteOptions? = routeOptions) {
        every {
            routeOptionsProvider.update(
                any(),
                any(),
                any()
            )
        } returns _routeOptions
    }
}
