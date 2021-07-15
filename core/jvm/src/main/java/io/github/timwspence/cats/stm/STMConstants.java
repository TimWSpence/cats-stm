/*
 * Copyright 2017-2021 TimWSpence
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

package io.github.timwspence.cats.stm;

final class STMConstants
{

    public static final byte PureT        = 0;
    public static final byte AllocT       = 1;
    public static final byte BindT        = 2;
    public static final byte HandleErrorT = 3;
    public static final byte GetT         = 4;
    public static final byte ModifyT      = 5;
    public static final byte OrElseT      = 6;
    public static final byte AbortT       = 7;
    public static final byte RetryT       = 8;

}
