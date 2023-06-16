/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mapper.converter;

import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.Arrays;
import java.util.List;

public class StringToListConverter implements Converter<String, List<String>> {
    @Override
    public List<String> convert(MappingContext<String, List<String>> context) {
        String source = context.getSource();
        return StringUtils.isEmpty(source) ? List.of(): Arrays.asList(context.getSource().split(","));
    }
}
