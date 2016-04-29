/**
 * Copyright 2010-2016 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.bsxm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deegree.geometry.validation.GeometryValidationEventHandler;
import org.deegree.geometry.validation.event.*;

/**
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot> de)
 *
 */
public class GmlGeoXGeometryValidationEventHandler
		implements GeometryValidationEventHandler {

	private Map<GeometryValidationEventType, List<GeometryValidationEvent>> eventsByType = new HashMap<GeometryValidationEventType, List<GeometryValidationEvent>>();

	public enum GeometryValidationEventType {
		/**
		 *
		 */
		CURVE_DISCONTINUITY, /**
								 *
								 */
		CURVE_SELF_INTERSECTION, /**
									 *
									 */
		EXTERIOR_RING_ORIENTATION, /**
									 *
									 */
		INTERIOR_RING_INTERSECTS_EXTERIOR, /**
											 *
											 */
		INTERIOR_RING_ORIENTATION, /**
									 *
									 */
		INTERIOR_RING_OUTSIDE_EXTERIOR, /**
										 *
										 */
		INTERIOR_RINGS_INTERSECT, /**
									 *
									 */
		INTERIOR_RINGS_NESTED, /**
								 *
								 */
		INTERIOR_RINGS_TOUCH, /**
								 *
								 */
		INTERIOR_RING_TOUCHES_EXTERIOR, /**
										 *
										 */
		RING_NOT_CLOSED
	}

	public boolean containsEventType(GeometryValidationEventType type) {
		return eventsByType.containsKey(type);
	}

	@Override
	public boolean fireEvent(GeometryValidationEvent event) {

		if (event instanceof CurveDiscontinuity) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.CURVE_DISCONTINUITY)) {
				list = eventsByType
						.get(GeometryValidationEventType.CURVE_DISCONTINUITY);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.CURVE_DISCONTINUITY, list);
			}

			list.add(event);
			return true;

		} else if (event instanceof CurveSelfIntersection) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.CURVE_SELF_INTERSECTION)) {
				list = eventsByType.get(
						GeometryValidationEventType.CURVE_SELF_INTERSECTION);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.CURVE_SELF_INTERSECTION,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof ExteriorRingOrientation) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.EXTERIOR_RING_ORIENTATION)) {
				list = eventsByType.get(
						GeometryValidationEventType.EXTERIOR_RING_ORIENTATION);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.EXTERIOR_RING_ORIENTATION,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof InteriorRingIntersectsExterior) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.INTERIOR_RING_INTERSECTS_EXTERIOR)) {
				list = eventsByType.get(
						GeometryValidationEventType.INTERIOR_RING_INTERSECTS_EXTERIOR);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.INTERIOR_RING_INTERSECTS_EXTERIOR,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof InteriorRingOrientation) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.INTERIOR_RING_ORIENTATION)) {
				list = eventsByType.get(
						GeometryValidationEventType.INTERIOR_RING_ORIENTATION);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.INTERIOR_RING_ORIENTATION,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof InteriorRingOutsideExterior) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.INTERIOR_RING_OUTSIDE_EXTERIOR)) {
				list = eventsByType.get(
						GeometryValidationEventType.INTERIOR_RING_OUTSIDE_EXTERIOR);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.INTERIOR_RING_OUTSIDE_EXTERIOR,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof InteriorRingsIntersect) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.INTERIOR_RINGS_INTERSECT)) {
				list = eventsByType.get(
						GeometryValidationEventType.INTERIOR_RINGS_INTERSECT);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.INTERIOR_RINGS_INTERSECT,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof InteriorRingsNested) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.INTERIOR_RINGS_NESTED)) {
				list = eventsByType
						.get(GeometryValidationEventType.INTERIOR_RINGS_NESTED);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.INTERIOR_RINGS_NESTED,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof InteriorRingsTouch) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.INTERIOR_RINGS_TOUCH)) {
				list = eventsByType
						.get(GeometryValidationEventType.INTERIOR_RINGS_TOUCH);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.INTERIOR_RINGS_TOUCH, list);
			}

			list.add(event);
			return true;

		} else if (event instanceof InteriorRingTouchesExterior) {

			List<GeometryValidationEvent> list;
			if (eventsByType.containsKey(
					GeometryValidationEventType.INTERIOR_RING_TOUCHES_EXTERIOR)) {
				list = eventsByType.get(
						GeometryValidationEventType.INTERIOR_RING_TOUCHES_EXTERIOR);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(
						GeometryValidationEventType.INTERIOR_RING_TOUCHES_EXTERIOR,
						list);
			}

			list.add(event);
			return true;

		} else if (event instanceof RingNotClosed) {

			List<GeometryValidationEvent> list;
			if (eventsByType
					.containsKey(GeometryValidationEventType.RING_NOT_CLOSED)) {
				list = eventsByType
						.get(GeometryValidationEventType.RING_NOT_CLOSED);
			} else {
				list = new ArrayList<GeometryValidationEvent>();
				eventsByType.put(GeometryValidationEventType.RING_NOT_CLOSED,
						list);
			}

			list.add(event);
			return true;

		} else {

			// TODO log occurrence of unrecognized event?
			return false;
		}
	}

}
