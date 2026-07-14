/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package log

import (
	"errors"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
)

func (ts *FieldTestSuite) TestFieldConstructors() {
	t := ts.T()
	assert.Equal(t, Field{Key: "k", Value: "v"}, String("k", "v"))
	assert.Equal(t, Field{Key: "k", Value: 42}, Int("k", 42))
	assert.Equal(t, Field{Key: "k", Value: true}, Bool("k", true))
	assert.Equal(t, Field{Key: "k", Value: 3.14}, Any("k", 3.14))

	err := errors.New("boom")
	assert.Equal(t, Field{Key: "error", Value: err}, Error(err))
}

func (ts *FieldTestSuite) TestConvertFields() {
	t := ts.T()
	fields := []Field{String("name", "test"), Int("count", 3)}
	attrs := convertFields(fields)
	assert.Len(t, attrs, 2)
}

type FieldTestSuite struct {
	suite.Suite
}

func TestFieldTestSuite(t *testing.T) {
	suite.Run(t, new(FieldTestSuite))
}
