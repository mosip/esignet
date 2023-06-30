/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mapper.converter;

import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.List;

public class ListToStringConverter implements Converter<List<String>, String> {
    @Override
    public String convert(MappingContext<List<String>, String> context) {
        List<String> source = context.getSource();
        return source == null ? "" : String.join(",", context.getSource());
    }
}