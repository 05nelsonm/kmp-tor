/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.runtime.core.address

import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.isLoopback
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4OrNull
import kotlin.test.*

class IPAddressV4UnitTest {

    @Test
    fun givenIPAddressV4_whenAnyHost_thenIsInstance() {
        assertIs<IPAddress.V4.AnyHost>("0.0.0.0".toIPAddressV4())
        assertIs<IPAddress.V4.AnyHost>(ByteArray(4).toIPAddressV4())
    }

    @Test
    fun givenIPAddressV4_whenTypicalLoopback_thenIsInstance() {
        assertTrue("127.0.0.1".toIPAddressV4().isLoopback())
        assertTrue(byteArrayOf(127, 0, 0, 1).toIPAddressV4().isLoopback())
    }

    @Test
    fun givenIPAddressV4_whenHighestValue_thenIsSuccessful() {
        val expected = "255.255.255.255"
        val actual = expected.toIPAddressV4()
        assertEquals(expected, actual.value)
    }

    @Test
    fun givenIPAddressV4_whenExceedsHighestValue_thenIsFailure() {
        assertNull("256.255.255.255".toIPAddressV4OrNull())
        assertNull("255.256.255.255".toIPAddressV4OrNull())
        assertNull("255.255.256.255".toIPAddressV4OrNull())
        assertNull("255.255.255.256".toIPAddressV4OrNull())
    }

    @Test
    fun givenIPAddressV4_whenRandom_thenIsSuccess() {
        for (address in TEST_ADDRESSES_IPV4.lines()) {
            address.toIPAddressV4()
        }
    }

    @Test
    fun givenIPAddressV4_whenCanonicalized_thenReturnsUnderlyingValue() {
        val expected = "127.0.0.1"
        val actual = expected.toIPAddressV4().canonicalHostName()
        assertEquals(expected, actual)
    }

    @Test
    fun givenIPAddressV4Url_whenFromString_thenReturnsSuccess() {
        val expected = "192.168.7.111".toIPAddressV4()
        val actual = "https://username:password@${expected.canonicalHostName()}:9822/some/path".toIPAddressV4()
        assertEquals(expected, actual)
    }

    companion object {
        val TEST_ADDRESSES_IPV4 = """
            66.250.238.47
            245.100.3.219
            7.193.64.71
            177.174.197.177
            251.38.30.74
            1.125.102.192
            33.65.46.41
            49.54.195.132
            183.53.174.180
            160.205.57.33
            240.228.92.15
            36.36.76.123
            252.115.160.95
            66.43.112.120
            215.181.18.22
            111.93.238.101
            217.150.102.111
            89.17.208.198
            67.139.50.9
            77.207.252.14
            247.216.218.215
            98.163.8.138
            47.119.70.74
            78.212.46.110
            136.44.21.95
            173.70.172.116
            34.8.236.174
            123.102.51.41
            130.170.172.254
            20.172.174.87
            209.68.112.62
            23.33.73.171
            67.67.226.188
            57.214.163.6
            165.222.213.187
            91.3.101.158
            115.188.147.244
            156.54.132.163
            49.69.16.120
            240.63.201.195
            54.25.125.217
            78.18.49.116
            54.221.251.164
            32.243.126.32
            248.68.6.29
            225.128.5.26
            91.166.135.54
            255.19.93.121
            239.148.239.211
            82.219.123.116
            123.130.164.84
            169.96.150.153
            37.110.99.97
            72.66.177.58
            30.18.213.9
            211.12.115.45
            205.83.96.118
            31.250.166.170
            212.193.196.127
            67.135.169.70
            108.50.85.203
            122.211.142.228
            77.27.118.80
            219.56.74.62
            249.211.95.180
            151.92.177.106
            164.227.205.47
            186.105.134.157
            163.154.220.52
            113.181.62.158
            89.253.19.212
            161.137.206.157
            61.140.231.237
            48.41.124.206
            135.101.120.47
            83.8.99.13
            58.39.127.216
            203.182.27.10
            228.145.68.70
            62.99.58.109
            155.101.222.165
            53.87.134.108
            206.160.191.214
            167.181.45.234
            192.235.141.192
            3.226.30.77
            55.76.236.255
            105.233.149.82
            214.136.35.146
            15.193.153.184
            138.60.148.131
            253.31.120.103
            237.79.106.249
            171.142.115.143
            50.26.8.167
            97.85.37.81
            225.145.171.54
            75.43.28.22
            41.49.166.206
            57.244.115.102
            73.160.63.54
            208.167.234.188
            186.138.27.94
            133.159.69.17
            217.238.171.206
            98.163.165.81
            71.75.181.231
            94.239.254.16
            23.102.224.36
            48.232.74.251
            121.149.232.182
            74.119.109.228
            58.27.73.47
            215.10.87.122
            251.237.148.148
            3.55.163.68
            113.68.9.47
            164.111.223.39
            250.29.155.40
            219.117.71.107
            192.127.100.175
            131.6.192.76
            28.68.53.123
            149.196.18.26
            134.230.178.50
            243.25.90.224
            200.199.164.216
            129.136.213.17
            237.97.185.102
            216.102.166.206
            77.171.131.14
            162.43.17.74
            196.18.176.12
            244.13.255.128
            128.23.163.153
            129.181.239.52
            136.32.85.151
            12.223.7.99
            10.244.179.6
            254.186.73.199
            28.189.79.23
            32.143.138.142
            24.116.5.32
            217.232.113.152
            34.18.165.223
            87.161.232.61
            82.19.170.251
            214.182.26.208
            250.246.86.105
            148.95.82.249
            239.51.109.149
            99.229.248.51
            119.126.130.66
            58.79.128.148
            224.160.131.219
            99.62.20.248
            92.53.241.167
            190.197.133.126
            184.169.70.49
            122.206.131.95
            98.85.11.127
            253.135.15.214
            232.215.87.38
            49.244.36.125
            32.97.119.252
            144.52.91.50
            23.240.159.166
            220.130.225.115
            127.2.139.184
            98.204.167.164
            196.3.250.172
            222.255.225.222
            27.170.35.135
            240.8.85.255
            0.214.121.218
            116.35.132.214
            163.212.7.127
            183.248.127.68
            255.21.171.191
            220.176.104.177
            85.167.229.131
            9.170.130.69
            86.73.199.103
            111.193.243.104
            123.57.96.0
            163.188.206.93
            77.204.172.188
            83.160.137.37
            145.90.155.77
            179.174.255.69
            253.11.69.246
            154.33.238.113
            173.148.5.229
            220.207.123.144
            20.127.230.20
            215.0.113.221
            95.49.141.113
            26.204.205.14
            3.46.187.39
            174.79.160.24
            155.105.48.161
            154.204.73.61
            233.169.8.160
            84.72.78.165
            111.61.129.49
            237.43.216.141
            129.48.249.20
            89.138.175.56
            17.166.129.253
            176.56.236.218
            30.201.226.91
            99.187.222.255
            220.33.178.80
            16.139.91.80
            253.213.171.238
            9.252.27.196
            199.222.181.72
            9.110.164.190
            39.185.156.37
            209.238.253.1
            185.103.122.143
            69.3.131.89
            235.91.122.247
            189.222.95.20
            10.82.184.242
            175.126.54.23
            204.75.91.83
            55.31.165.112
            202.92.202.250
            196.1.200.160
            224.75.13.82
            193.22.228.20
            186.242.195.7
            234.103.242.145
            233.232.5.30
            7.166.189.206
            98.87.172.201
            252.21.180.12
            94.54.108.20
            26.46.64.61
            59.245.101.2
            93.214.35.185
            169.176.92.2
            23.5.243.219
            97.51.205.3
            212.97.255.167
            56.210.170.231
            126.199.168.134
            141.251.251.50
            158.133.237.53
            141.175.77.141
            51.11.129.199
            143.17.227.169
            69.244.96.206
            188.106.114.167
            199.146.60.119
            40.143.106.111
            80.120.187.120
            127.205.12.250
            204.229.63.75
            67.91.111.187
            120.103.107.9
            247.255.198.185
            141.176.3.147
            239.134.201.45
            164.44.63.123
            13.0.86.161
            144.7.197.12
            89.54.31.224
            18.136.33.172
            68.248.11.200
            101.209.102.93
            136.243.24.33
            149.128.104.8
            114.252.44.181
            78.184.121.77
            202.151.51.169
            179.125.32.225
            150.194.51.242
            109.207.146.74
            185.203.12.196
            131.130.162.138
            113.237.191.127
            110.205.87.67
            193.100.0.170
            169.223.141.57
            99.213.83.72
            59.45.1.81
            200.176.149.157
            19.36.207.200
            244.74.136.167
            153.170.182.212
            125.149.150.246
            232.220.98.16
            178.231.244.58
            160.164.16.164
            147.125.78.135
            124.55.103.133
            33.171.158.139
            44.209.94.209
            130.160.240.69
            237.162.244.210
            108.3.37.170
            167.238.153.129
            191.96.223.178
            203.17.85.204
            251.152.31.10
            27.137.142.167
            175.200.232.193
            101.95.189.178
            11.196.180.248
            254.239.27.119
            21.194.135.155
            85.115.183.18
            187.232.253.224
            132.207.41.58
            122.136.213.12
            142.23.189.255
            155.206.207.213
            37.96.188.169
            165.67.110.120
            140.146.110.225
            145.129.136.243
            196.99.95.116
            224.226.86.37
            250.161.237.166
            142.42.38.107
            209.33.251.48
            117.69.52.213
            144.15.215.37
            137.201.51.62
            137.173.77.155
            119.58.115.58
            255.43.11.245
            78.184.238.102
            130.64.206.197
            49.26.33.217
            200.251.240.35
            255.157.121.20
            141.82.82.22
            110.61.43.181
            243.236.49.238
            222.125.170.60
            155.62.46.25
            192.193.193.152
            118.230.243.103
            81.157.232.254
            55.24.50.144
            106.46.95.228
            66.40.12.119
            212.207.113.170
            140.172.90.182
            145.184.62.247
            16.196.3.35
            55.137.120.121
            95.125.125.123
            76.80.112.47
            118.217.243.35
            148.198.39.174
            20.12.216.244
            202.49.113.82
            90.0.108.62
            116.110.13.8
            25.171.44.215
            227.208.86.244
            228.115.23.166
            247.211.172.65
            176.209.100.74
            155.87.167.151
            58.219.149.25
            96.110.46.214
            26.64.210.15
            167.138.212.82
            50.156.120.92
            31.75.208.95
            36.116.231.159
            18.246.8.87
            153.185.100.173
            93.121.182.252
            34.33.162.1
            12.187.3.53
            25.212.74.27
            254.19.174.145
            173.253.103.6
            124.217.121.254
            25.70.165.18
            233.211.159.108
            79.91.40.70
            19.167.67.79
            83.112.97.184
            241.103.170.243
            238.172.36.46
            107.204.27.78
            224.173.34.133
            215.65.115.158
            102.121.61.46
            52.119.166.155
            180.76.107.120
            222.225.244.179
            87.34.88.104
            93.33.68.170
            179.250.0.188
            157.68.8.196
            76.176.132.4
            187.243.63.36
            34.208.234.252
            140.77.63.143
            130.236.248.84
            53.52.163.89
            87.27.241.178
            188.93.138.150
            26.174.212.220
            135.14.151.200
            76.83.249.59
            10.36.133.23
            180.140.6.212
            10.20.12.6
            75.139.126.214
            178.214.176.112
            70.107.185.2
            192.169.199.35
            19.222.186.146
            231.200.163.188
            197.100.67.63
            54.27.102.244
            186.14.78.50
            142.224.21.17
            80.160.42.142
            59.147.189.245
            96.58.210.193
            253.43.207.33
            177.94.254.106
            53.69.165.80
            253.175.42.34
            223.246.33.25
            23.245.108.53
            138.218.54.177
            173.14.184.25
            239.6.183.70
            197.51.193.6
            174.144.29.10
            38.47.128.112
            46.247.135.234
            48.231.14.104
            65.127.22.7
            210.113.172.147
            240.69.168.160
            239.50.91.254
            105.191.68.39
            201.181.27.183
            251.118.35.232
            228.137.161.39
            234.165.47.135
            240.105.196.230
            164.225.186.20
            60.146.86.64
            142.50.168.120
            178.209.246.13
            73.38.231.46
            110.10.231.220
            78.201.129.125
            96.184.20.90
            92.104.124.189
            171.112.122.73
            110.97.151.16
            150.154.125.65
            116.213.143.222
            26.181.132.44
            29.173.251.174
            37.162.190.74
            181.7.9.248
            238.191.189.163
            184.196.254.75
            31.210.50.126
            163.111.138.243
            244.168.33.144
            82.70.239.253
            76.246.132.90
            211.206.132.231
            26.203.18.165
            243.149.220.55
            39.9.167.157
            243.227.38.95
            193.86.66.170
            189.21.43.78
            207.123.106.43
            85.124.150.120
            54.170.37.46
            166.252.42.32
            179.225.22.66
            152.234.66.93
            28.83.134.119
            246.232.233.196
            111.127.170.175
            86.216.102.241
            69.87.196.164
            145.79.227.150
            2.215.200.121
            158.90.139.239
            97.7.198.200
            105.245.154.53
            13.54.181.127
            160.201.152.121
            244.102.70.204
            113.42.250.66
            232.60.210.164
            165.250.234.109
            59.149.12.35
            242.96.113.91
            74.189.246.174
            148.40.9.190
            31.125.27.147
            198.223.210.120
            11.10.220.57
            51.82.147.59
            62.184.18.86
            21.234.116.234
            177.73.22.138
            144.245.55.1
            143.220.214.240
            87.25.208.205
            178.134.87.60
            65.211.203.122
            149.31.165.233
            115.187.65.239
            246.135.237.223
            246.77.159.202
            87.171.47.202
            101.56.238.114
            97.195.241.241
            56.151.42.211
            210.209.243.158
            162.220.192.87
            179.91.39.145
            195.88.63.45
            157.171.75.108
            173.47.18.69
            125.212.227.84
            161.9.47.47
            63.196.160.98
            143.246.94.193
            209.97.13.155
            13.216.79.21
            72.246.206.55
            255.95.84.47
            204.217.195.17
            160.178.253.199
            3.174.104.86
            213.15.166.236
            81.199.158.0
            57.61.99.208
            106.39.75.101
            81.97.200.123
            179.124.208.60
            174.0.148.57
            79.183.178.253
            134.229.21.43
            163.121.144.237
            230.49.120.143
            42.115.194.38
            27.157.35.87
            185.158.251.185
            201.189.149.198
            64.16.210.116
            243.133.202.60
            86.21.59.29
            51.248.169.184
            170.142.175.228
            189.232.154.93
            250.254.225.95
            90.234.242.149
            187.219.15.67
            196.30.9.26
            114.245.58.27
            62.164.50.19
            34.111.50.106
            229.166.22.100
            184.128.252.45
            51.177.18.179
            65.38.109.130
            217.247.220.37
            57.199.53.89
            74.183.81.72
            100.153.148.84
            126.60.61.90
            94.40.28.174
            127.22.211.143
            157.206.253.71
            133.226.32.116
            122.180.196.2
            249.126.100.193
            13.157.166.61
            22.36.202.58
            247.40.33.229
            101.164.203.62
            237.167.143.242
            237.76.17.31
            44.14.67.231
            28.110.36.78
            41.194.74.76
            17.0.81.125
            248.240.120.16
            25.35.121.212
            21.63.20.182
            60.95.5.186
            190.195.140.189
            55.44.38.174
            64.107.69.227
            52.238.87.159
            98.244.12.255
            74.199.157.105
            113.188.136.234
            166.9.30.246
            54.162.156.57
            70.253.170.195
            86.43.163.143
            93.207.197.50
            187.25.7.50
            208.33.85.50
            215.199.43.19
            1.142.43.113
            23.212.162.179
            135.89.212.87
            182.62.252.99
            121.79.4.212
            89.76.196.126
            204.6.195.156
            29.14.184.206
            97.251.167.76
            121.218.97.207
            112.83.87.217
            248.1.145.8
            153.190.207.226
            51.76.190.168
            231.119.78.215
            22.58.198.195
            13.161.58.93
            74.230.175.23
            75.181.142.191
            70.255.137.247
            30.188.142.12
            66.205.40.252
            189.35.187.145
            149.129.27.45
            63.198.224.156
            72.181.237.0
            135.165.55.214
            120.87.170.76
            239.17.168.215
            79.87.82.172
            2.163.229.221
            202.214.65.150
            104.173.128.87
            107.80.38.173
            62.66.233.252
            19.179.78.234
            220.233.21.11
            178.150.191.234
            195.244.108.40
            129.60.127.197
            130.17.50.244
            30.20.105.9
            93.190.185.143
            103.185.188.99
            233.202.218.69
            51.150.0.239
            162.15.80.125
            9.179.173.113
            252.28.144.154
            61.19.141.103
            195.149.203.33
            142.118.196.97
            93.83.178.44
            148.243.197.80
            150.44.97.224
            233.79.181.68
            14.228.25.23
            33.204.175.76
            7.149.76.96
            44.198.106.57
            23.62.236.176
            52.171.223.178
            50.251.113.168
            117.229.168.161
            142.171.129.4
            254.39.55.182
            42.47.106.88
            197.153.154.8
            111.155.118.94
            25.86.62.207
            145.86.248.219
            223.32.75.13
            183.36.9.106
            114.237.38.34
            200.148.155.101
            38.83.206.159
            13.154.130.102
            125.145.195.10
            27.98.174.145
            238.56.139.240
            246.15.149.213
            200.178.55.127
            102.159.0.189
            160.194.70.72
            4.59.187.116
            167.189.58.29
            157.41.122.163
            54.39.20.135
            64.26.35.165
            127.24.137.16
            49.7.164.90
            16.38.160.90
            12.87.128.238
            98.14.34.46
            99.100.60.76
            132.67.45.52
            166.170.97.23
            133.66.28.83
            227.191.141.9
            176.166.47.233
            237.17.186.254
            93.238.65.87
            29.37.62.42
            50.120.107.138
            169.207.149.161
            14.241.39.156
            85.3.98.111
            131.40.22.176
            91.146.7.95
            205.3.65.199
            147.153.48.193
            41.17.182.246
            138.183.182.31
            146.171.255.125
            40.215.135.151
            51.72.158.40
            171.159.61.235
            0.33.38.59
            9.78.130.187
            105.104.227.85
            137.102.79.101
            152.216.7.67
            175.253.167.142
            73.189.4.90
            87.172.76.248
            199.196.216.67
            51.226.99.69
            99.21.111.101
            253.46.252.255
            135.132.185.146
            227.106.200.241
            205.152.37.180
            227.122.147.10
            182.44.31.213
            46.96.139.243
            95.26.22.13
            117.91.229.210
            245.241.195.162
            175.156.91.23
            25.44.147.82
            5.170.72.146
            185.225.248.244
            111.167.38.37
            33.61.214.32
            51.228.241.211
            154.123.80.82
            12.241.59.149
            165.172.191.70
            30.62.135.45
            84.107.77.85
            72.99.126.137
            141.46.194.119
            158.34.49.252
            66.118.220.88
            231.178.187.143
            203.198.247.236
            112.236.209.42
            112.41.242.142
            155.162.147.61
            137.3.43.238
            58.189.188.45
            65.121.39.164
            122.7.243.34
            143.129.4.8
            0.122.28.53
            84.19.169.75
            138.156.247.211
            13.77.180.89
            153.69.118.37
            202.160.139.124
            172.40.181.90
            222.122.87.67
            235.15.152.228
            129.227.137.7
            181.74.234.169
            185.48.26.95
            248.18.132.58
            55.37.20.105
            43.121.24.113
            22.11.96.26
            71.78.5.46
            105.202.246.39
            111.172.35.31
            43.50.68.239
            166.163.254.216
            81.80.175.98
            218.175.10.172
            255.83.108.6
            162.161.171.160
            159.41.12.172
            100.79.212.210
            154.118.50.209
            92.72.224.252
            39.92.1.220
            156.17.232.63
            54.238.97.96
            204.76.243.94
            62.19.55.151
            132.37.39.52
            125.33.244.93
            127.218.72.82
            160.31.240.139
            7.64.177.81
            110.254.246.72
            48.33.119.50
            171.167.217.221
            14.248.124.111
            54.217.160.39
            192.98.143.110
            219.43.232.210
            186.254.250.117
            117.125.66.114
            11.146.20.12
            178.229.75.113
            27.74.243.96
            201.1.199.128
            205.199.129.166
            189.80.52.254
            225.140.165.188
            142.132.186.29
            189.247.128.116
            82.182.189.146
            182.75.60.21
            224.14.177.161
            105.183.15.200
            133.47.51.174
            88.160.119.254
            165.94.51.64
            174.17.32.46
            79.54.37.57
            5.233.35.58
            18.152.222.20
            183.220.67.196
            38.54.153.211
            78.242.100.109
            134.157.227.180
            238.14.216.141
            217.19.111.224
            76.84.60.211
            120.150.164.20
            116.136.126.14
            242.51.139.83
            82.116.223.58
            227.86.119.75
            32.184.162.227
            162.58.67.15
            55.137.10.56
            178.82.54.110
            170.105.33.176
            169.58.160.88
            75.149.97.213
            188.34.107.244
            205.96.44.214
            168.35.163.72
            90.51.246.22
            18.235.213.185
            126.97.47.205
            122.1.242.39
            252.170.126.43
            234.162.41.84
            158.252.117.114
            131.169.205.185
            84.49.26.160
            155.50.194.138
            243.29.144.216
            119.142.161.201
            172.2.185.18
            9.45.227.124
            101.41.93.32
            164.56.254.211
            106.237.170.151
            11.207.13.52
            205.255.145.247
            156.216.45.158
            171.250.130.5
            101.177.22.80
            167.56.35.207
            139.63.224.246
            90.249.186.113
            183.233.194.108
            226.106.184.240
            76.82.72.28
            57.139.215.143
            68.215.5.174
            74.200.91.134
            84.55.119.33
            65.8.183.99
            251.91.92.75
            29.196.27.227
            243.68.198.16
            97.89.202.133
            23.165.213.97
            226.81.24.169
            80.236.213.174
            21.10.117.147
            210.202.123.239
            17.110.39.1
            132.19.19.93
            143.143.225.210
            180.167.124.242
            14.36.166.215
            79.149.235.135
            68.125.153.134
            201.254.118.170
            52.216.255.93
            191.244.189.246
            185.217.46.231
            201.201.214.108
            83.44.240.249
            169.180.196.132
            16.239.123.110
            44.147.78.50
            202.154.151.97
            180.27.123.24
            55.88.58.171
            136.181.170.164
            177.36.5.159
            146.48.81.221
            60.25.103.118
            201.182.27.226
            75.242.159.25
            93.15.22.86
            7.216.99.100
            208.5.188.210
            250.171.42.54
            132.78.0.183
            162.7.222.189
            110.116.82.73
            212.51.214.101
            24.84.151.53
            246.70.37.36
            225.107.191.12
            27.73.175.234
            216.161.203.82
            105.75.118.220
            132.190.212.24
            89.191.216.75
            202.28.226.119
            29.115.122.75
            40.91.122.42
            66.109.31.176
            66.150.107.171
            129.187.143.111
            239.171.180.181
            1.5.117.104
            73.92.180.7
            153.151.231.182
            4.78.42.192
            180.248.2.210
            89.2.158.156
            137.27.34.46
            33.134.219.230
            254.255.249.62
            241.219.50.37
            160.54.10.216
            134.156.178.34
            52.148.118.231
            196.60.250.227
            45.193.110.103
            76.62.71.114
            140.75.57.127
            175.105.105.152
            253.215.15.191
            146.185.126.153
            35.7.11.173
            204.117.58.236
            10.83.5.78
            75.104.252.147
            239.226.216.114
            85.22.219.135
            109.37.88.141
            155.34.125.39
            217.112.90.128
            53.201.193.53
            69.221.126.158
            117.208.144.194
            43.155.141.6
            31.213.80.198
            200.34.14.16
            19.49.249.16
        """.trimIndent()
    }
}
