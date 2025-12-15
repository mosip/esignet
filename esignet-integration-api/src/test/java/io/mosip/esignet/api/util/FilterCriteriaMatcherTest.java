/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.FilterCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class FilterCriteriaMatcherTest {

    @InjectMocks
    private FilterCriteriaMatcher filterCriteriaMatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KEY = "testKey";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(filterCriteriaMatcher, "objectMapper", this.objectMapper);
    }

    @Test
    void testDoMatch_WhenSingleValueEqualsStoredValue_returnTrue() {
        Map<String, Object> filter = new HashMap<>();
        FilterCriteria criteria = new FilterCriteria();
        criteria.setValue("expectedValue");
        filter.put(KEY, criteria);
        JsonNode storedVerificationDetail = objectMapper.createObjectNode().put(KEY, "expectedValue");
        assertTrue(filterCriteriaMatcher.doMatch(filter, KEY, storedVerificationDetail));
    }

    @Test
    void testDoMatch_WhenStoredValueIsInValuesList_returnTrue() {
        Map<String, Object> filter = new HashMap<>();
        FilterCriteria criteria = new FilterCriteria();
        criteria.setValues(Collections.singletonList("expectedValue"));
        filter.put(KEY, criteria);
        JsonNode storedVerificationDetail = objectMapper.createObjectNode().put(KEY, "expectedValue");
        assertTrue(filterCriteriaMatcher.doMatch(filter, KEY, storedVerificationDetail));
    }

    @Test
    void testDoMatch_MaxAgeValid_returnTrue() {
        Map<String, Object> filter = new HashMap<>();
        FilterCriteria criteria = new FilterCriteria();
        criteria.setMax_age(1000000); // Large enough to be valid
        filter.put(KEY, criteria);
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mmX").format(new java.util.Date());
        JsonNode storedVerificationDetail = objectMapper.createObjectNode().put(KEY, now);
        assertTrue(filterCriteriaMatcher.doMatch(filter, KEY, storedVerificationDetail));
    }

    @Test
    void testDoMatch_NullFilterValue_returnTrue() {
        Map<String, Object> filter = new HashMap<>();
        filter.put(KEY, null);
        JsonNode storedVerificationDetail = objectMapper.createObjectNode();
        assertTrue(filterCriteriaMatcher.doMatch(filter, KEY, storedVerificationDetail));
    }

    @Test
    void testDoMatch_StoredValueNull_returnFalse() {
        Map<String, Object> filter = new HashMap<>();
        FilterCriteria criteria = new FilterCriteria();
        criteria.setValue("expectedValue");
        filter.put(KEY, criteria);
        JsonNode storedVerificationDetail = objectMapper.createObjectNode(); // KEY not present
        assertFalse(filterCriteriaMatcher.doMatch(filter, KEY, storedVerificationDetail));
    }

    @Test
    void testDoMatch_InvalidDateFormat_returnFalse() {
        Map<String, Object> filter = new HashMap<>();
        FilterCriteria criteria = new FilterCriteria();
        criteria.setMax_age(1000);
        filter.put(KEY, criteria);
        JsonNode storedVerificationDetail = objectMapper.createObjectNode().put(KEY, "invalid-date");
        assertFalse(filterCriteriaMatcher.doMatch(filter, KEY, storedVerificationDetail));
    }
}
