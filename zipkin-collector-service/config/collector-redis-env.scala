/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.twitter.zipkin.builder.{ZipkinServerBuilder, Scribe}
import com.twitter.zipkin.collector.HttpCollector
import com.twitter.zipkin.redis
import com.twitter.zipkin.collector.builder.CollectorServiceBuilder
import com.twitter.zipkin.storage.Store

val redisConfig = redis.Config.fromEnv()

val redisBuilder = Store.Builder(
    redis.StorageBuilder(redisConfig),
    redis.IndexBuilder(redisConfig)
)

// TODO - Change this
//CollectorServiceBuilder(Scribe.Interface(categories = Set("zipkin")))
//  .writeTo(redisBuilder)
CollectorServiceBuilder(HttpCollector.Interface(), serverBuilder = ZipkinServerBuilder(8080, 9000)).writeTo(redisBuilder)