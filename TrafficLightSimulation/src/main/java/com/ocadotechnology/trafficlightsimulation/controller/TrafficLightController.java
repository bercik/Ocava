/*
 * Copyright © 2017-2020 Ocado (Ocava)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ocadotechnology.trafficlightsimulation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.ocadotechnology.config.Config;
import com.ocadotechnology.event.scheduling.EventScheduler;
import com.ocadotechnology.notification.NotificationRouter;
import com.ocadotechnology.trafficlightsimulation.TrafficConfig;
import com.ocadotechnology.trafficlightsimulation.TrafficConfig.TrafficLight;

public class TrafficLightController {

    private static final Logger logger = LoggerFactory.getLogger(TrafficLightController.class);
    private final boolean automaticTrafficLightChangeEnabled;

    public enum State {
        RED,
        GREEN;

        private static State getInverse(State state) {
            switch (state) {
                case RED:
                    return State.GREEN;
                case GREEN:
                    return State.RED;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private final EventScheduler scheduler;
    private final ImmutableMap<State, Long> stateDurations;
    private State currentState;

    public TrafficLightController(EventScheduler scheduler, Config<TrafficConfig> trafficConfig) {
        this.scheduler = scheduler;

        this.stateDurations = ImmutableMap.<State, Long>builder()
                .put(State.RED, trafficConfig.getTime(TrafficLight.RED_LIGHT_INTERVAL))
                .put(State.GREEN, trafficConfig.getTime(TrafficLight.GREEN_LIGHT_INTERVAL))
                .build();

        this.automaticTrafficLightChangeEnabled = trafficConfig.getBoolean(TrafficLight.ENABLE_AUTOMATIC_CHANGE);

        if (automaticTrafficLightChangeEnabled) {
            scheduler.doNow(() -> changeState(trafficConfig.getEnum(TrafficLight.INITIAL_STATE, TrafficLightController.State.class)));
        }
    }

    public void changeState(State newState) {
        currentState = newState;

        logger.info("Traffic light is now {}", currentState);

        NotificationRouter.get().broadcast(new TrafficLightChangedNotification(currentState));
        if (automaticTrafficLightChangeEnabled) {
            State nextState = State.getInverse(currentState);
            scheduler.doIn(stateDurations.get(currentState), () -> changeState(nextState), "Traffic light change state event");
        }
    }

}
