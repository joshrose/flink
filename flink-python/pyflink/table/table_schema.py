################################################################################
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
# limitations under the License.
################################################################################
from typing import List, Optional, Union

from pyflink.java_gateway import get_gateway
from pyflink.table.types import DataType, RowType, _to_java_data_type, _from_java_data_type
from pyflink.util.api_stability_decorators import Deprecated
from pyflink.util.java_utils import to_jarray

__all__ = ['TableSchema']


@Deprecated(since="2.1.0", detail="""
This class has been deprecated as part of FLIP-164. It has been replaced by two more dedicated
classes :class:`~pyflink.table.Schema` and :class:`~pyflink.table.catalog.ResolvedSchema`.
Use :class:`~pyflink.table.Schema` for declaration in APIs.
:class:`~pyflink.table.catalog.ResolvedSchema` is offered by the framework after resolution and
validation.
""")
class TableSchema(object):
    """
    A table schema that represents a table's structure with field names and data types.
    """
    def __init__(self, field_names: List[str] = None, data_types: List[DataType] = None,
                 j_table_schema=None):
        if j_table_schema is None:
            gateway = get_gateway()
            j_field_names = to_jarray(gateway.jvm.String, field_names)
            j_data_types = to_jarray(gateway.jvm.DataType,
                                     [_to_java_data_type(item) for item in data_types])
            self._j_table_schema = gateway.jvm.TableSchema.builder()\
                .fields(j_field_names, j_data_types).build()
        else:
            self._j_table_schema = j_table_schema

    def copy(self) -> 'TableSchema':
        """
        Returns a deep copy of the table schema.

        :return: A deep copy of the table schema.
        """
        return TableSchema(j_table_schema=self._j_table_schema.copy())

    def get_field_data_types(self) -> List[DataType]:
        """
        Returns all field data types as a list.

        :return: A list of all field data types.
        """
        return [_from_java_data_type(item) for item in self._j_table_schema.getFieldDataTypes()]

    def get_field_data_type(self, field: Union[int, str]) -> Optional[DataType]:
        """
        Returns the specified data type for the given field index or field name.

        :param field: The index of the field or the name of the field.
        :return: The data type of the specified field.
        """
        if not isinstance(field, (int, str)):
            raise TypeError("Expected field index or field name, got %s" % type(field))
        optional_result = self._j_table_schema.getFieldDataType(field)
        if optional_result.isPresent():
            return _from_java_data_type(optional_result.get())
        else:
            return None

    def get_field_count(self) -> int:
        """
        Returns the number of fields.

        :return: The number of fields.
        """
        return self._j_table_schema.getFieldCount()

    def get_field_names(self) -> List[str]:
        """
        Returns all field names as a list.

        :return: The list of all field names.
        """
        return list(self._j_table_schema.getFieldNames())

    def get_field_name(self, field_index: int) -> Optional[str]:
        """
        Returns the specified name for the given field index.

        :param field_index: The index of the field.
        :return: The field name.
        """
        optional_result = self._j_table_schema.getFieldName(field_index)
        if optional_result.isPresent():
            return optional_result.get()
        else:
            return None

    def to_row_data_type(self) -> RowType:
        """
        Converts a table schema into a (nested) data type describing a
        :func:`pyflink.table.types.DataTypes.ROW`.

        :return: The row data type.
        """
        return _from_java_data_type(self._j_table_schema.toRowDataType())

    def __repr__(self):
        return self._j_table_schema.toString()

    def __eq__(self, other):
        return isinstance(other, self.__class__) and self._j_table_schema == other._j_table_schema

    def __hash__(self):
        return self._j_table_schema.hashCode()

    def __ne__(self, other):
        return not self.__eq__(other)

    @classmethod
    def builder(cls):
        return TableSchema.Builder()

    class Builder(object):
        """
        Builder for creating a :class:`TableSchema`.
        """

        def __init__(self):
            self._field_names = []
            self._field_data_types = []

        def field(self, name: str, data_type: DataType) -> 'TableSchema.Builder':
            """
            Add a field with name and data type.

            The call order of this method determines the order of fields in the schema.

            :param name: The field name.
            :param data_type: The field data type.
            :return: This object.
            """
            assert name is not None
            assert data_type is not None
            self._field_names.append(name)
            self._field_data_types.append(data_type)
            return self

        def build(self) -> 'TableSchema':
            """
            Returns a :class:`TableSchema` instance.

            :return: The :class:`TableSchema` instance.
            """
            return TableSchema(self._field_names, self._field_data_types)
