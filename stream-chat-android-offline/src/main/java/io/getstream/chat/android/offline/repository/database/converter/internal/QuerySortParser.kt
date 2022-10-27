/*
 * Copyright (c) 2014-2022 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.chat.android.offline.repository.database.converter.internal

import io.getstream.chat.android.client.api.models.querysort.ComparableFieldProvider
import io.getstream.chat.android.client.api.models.querysort.QuerySortByField
import io.getstream.chat.android.client.api.models.querysort.QuerySorter
import io.getstream.chat.android.client.api.models.querysort.SortDirection
import io.getstream.chat.android.client.api.models.querysort.internal.SortSpecification

public class QuerySortParser<T : ComparableFieldProvider> {

    public fun fromRawInfo(
        specs: List<Map<String, Any>>,
    ): QuerySorter<T> {
        return specs.fold(QuerySortByField()) { sort, sortSpecMap ->
            val fieldName = sortSpecMap[QuerySorter.KEY_FIELD_NAME] as? String
                ?: error("Cannot parse sortSpec to query sort\n$sortSpecMap")
            val direction = (sortSpecMap[QuerySorter.KEY_DIRECTION] as? Number)?.toInt()
                ?: error("Cannot parse sortSpec to query sort\n$sortSpecMap")

            createQuerySort(sort, fieldName, direction)
                ?: error("Cannot parse sortSpec to query sort: $sortSpecMap")
        }
    }

    public fun fromSpecifications(specs: List<SortSpecification<T>>): QuerySorter<T> {
        return specs.fold(QuerySortByField()) { querySort, sortSpec ->
            val fieldName = sortSpec.sortAttribute.name
            val direction = sortSpec.sortDirection.value

            createQuerySort(querySort, fieldName, direction)
                ?: error("Cannot parse sortSpec to query sort: $sortSpec")
        }
    }

    private fun createQuerySort(
        querySort: QuerySortByField<T>,
        fieldName: String,
        direction: Int,
    ): QuerySortByField<T>? {
        return when (direction) {
            SortDirection.ASC.value -> querySort.asc(fieldName)
            SortDirection.DESC.value -> querySort.desc(fieldName)
            else -> null
        }
    }
}