package edu.ums.web.api;

import java.util.List;

/** A keyset page: {@code next} is the id to send as "after" for the following page, or null at the end. */
public record PageResult<T>(List<T> items, Long next) {
}
