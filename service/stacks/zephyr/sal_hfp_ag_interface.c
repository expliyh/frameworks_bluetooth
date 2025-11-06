/****************************************************************************
 *  Copyright (C) 2025 Xiaomi Corporation
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
 ***************************************************************************/

#include "sal_hfp_ag_interface.h"
#include <zephyr/bluetooth/classic/hfp_ag.h>
#include <zephyr/logging/log.h>

LOG_MODULE_REGISTER(sal_hfp_ag, CONFIG_BT_HFP_AG_LOG_LEVEL);

static void ag_connected(struct bt_conn *conn, struct bt_hfp_ag *ag)
{
    (void)conn;
    (void)ag;
}

static void ag_disconnected(struct bt_hfp_ag *ag)
{
    (void)ag;
}

static void ag_sco_connected(struct bt_hfp_ag *ag, struct bt_conn *sco_conn)
{
    (void)ag;
    (void)sco_conn;
}

static void ag_sco_disconnected(struct bt_conn *sco_conn, uint8_t reason)
{
    (void)sco_conn;
    (void)reason;
}

static int ag_get_ongoing_call(struct bt_hfp_ag *ag)
{
    (void)ag;
    return 0;
}

static int ag_memory_dial(struct bt_hfp_ag *ag, const char *location, char **number)
{
    (void)ag;
    (void)location;
    (void)number;
    return 0;
}

static int ag_number_call(struct bt_hfp_ag *ag, const char *number)
{
    (void)ag;
    (void)number;
    return 0;
}

static void ag_outgoing(struct bt_hfp_ag *ag, struct bt_hfp_ag_call *call, const char *number)
{
    (void)ag;
    (void)call;
    (void)number;
}

static void ag_incoming(struct bt_hfp_ag *ag, struct bt_hfp_ag_call *call, const char *number)
{
    (void)ag;
    (void)call;
    (void)number;
}

static void ag_incoming_held(struct bt_hfp_ag_call *call)
{
    (void)call;
}

static void ag_ringing(struct bt_hfp_ag_call *call, bool in_band)
{
    (void)call;
    (void)in_band;
}

static void ag_accept(struct bt_hfp_ag_call *call)
{
    (void)call;
}

static void ag_held(struct bt_hfp_ag_call *call)
{
    (void)call;
}

static void ag_retrieve(struct bt_hfp_ag_call *call)
{
    (void)call;
}

static void ag_reject(struct bt_hfp_ag_call *call)
{
    (void)call;
}

static void ag_terminate(struct bt_hfp_ag_call *call)
{
    (void)call;
}

static void ag_codec(struct bt_hfp_ag *ag, uint32_t ids)
{
    (void)ag;
    (void)ids;
}

static void ag_codec_negotiate(struct bt_hfp_ag *ag, int err)
{
    (void)ag;
    (void)err;
}

static void ag_audio_connect_req(struct bt_hfp_ag *ag)
{
    (void)ag;
}

static void ag_vgm(struct bt_hfp_ag *ag, uint8_t gain)
{
    (void)ag;
    (void)gain;
}

static void ag_vgs(struct bt_hfp_ag *ag, uint8_t gain)
{
    (void)ag;
    (void)gain;
}

static void ag_ecnr_turn_off(struct bt_hfp_ag *ag)
{
    (void)ag;
}

static void ag_explicit_call_transfer(struct bt_hfp_ag *ag)
{
    (void)ag;
}

static void ag_voice_recognition(struct bt_hfp_ag *ag, bool activate)
{
    (void)ag;
    (void)activate;
}

static void ag_ready_to_accept_audio(struct bt_hfp_ag *ag)
{
    (void)ag;
}

static int ag_request_phone_number(struct bt_hfp_ag *ag, char **number)
{
    (void)ag;
    (void)number;
    return 0;
}

static void ag_transmit_dtmf_code(struct bt_hfp_ag *ag, char code)
{
    (void)ag;
    (void)code;
}

static int ag_subscriber_number(struct bt_hfp_ag *ag, bt_hfp_ag_query_subscriber_func_t func)
{
    (void)ag;
    (void)func;
    return 0;
}

static void ag_hf_indicator_value(struct bt_hfp_ag *ag, enum hfp_ag_hf_indicators indicator, uint32_t value)
{
    (void)ag;
    (void)indicator;
    (void)value;
}

/* --- 全局回调结构 --- */
static const struct bt_hfp_ag_cb g_hfp_ag_cb = {
    .connected = ag_connected,
    .disconnected = ag_disconnected,
    .sco_connected = ag_sco_connected,
    .sco_disconnected = ag_sco_disconnected,
    .get_ongoing_call = ag_get_ongoing_call,
    .memory_dial = ag_memory_dial,
    .number_call = ag_number_call,
    .outgoing = ag_outgoing,
    .incoming = ag_incoming,
    .incoming_held = ag_incoming_held,
    .ringing = ag_ringing,
    .accept = ag_accept,
    .held = ag_held,
    .retrieve = ag_retrieve,
    .reject = ag_reject,
    .terminate = ag_terminate,
    .codec = ag_codec,
    .codec_negotiate = ag_codec_negotiate,
    .audio_connect_req = ag_audio_connect_req,
    .vgm = ag_vgm,
    .vgs = ag_vgs,
    .ecnr_turn_off = ag_ecnr_turn_off,
    .explicit_call_transfer = ag_explicit_call_transfer,
    .voice_recognition = ag_voice_recognition,
    .ready_to_accept_audio = ag_ready_to_accept_audio,
    .request_phone_number = ag_request_phone_number,
    .transmit_dtmf_code = ag_transmit_dtmf_code,
    .subscriber_number = ag_subscriber_number,
    .hf_indicator_value = ag_hf_indicator_value,
};

bt_status_t bt_sal_hfp_ag_init(uint32_t features, uint8_t max_connection)
{
    (void)features;
    (void)max_connection;

    bt_hfp_ag_register(&g_hfp_ag_cb);
    return BT_STATUS_SUCCESS;
}

void bt_sal_hfp_ag_cleanup(void)
{
}

bt_status_t bt_sal_hfp_ag_connect(bt_address_t* addr)
{
    (void)addr;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_disconnect(bt_address_t* addr)
{
    (void)addr;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_connect_audio(bt_address_t* addr)
{
    (void)addr;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_disconnect_audio(bt_address_t* addr)
{
    (void)addr;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_start_voice_recognition(bt_address_t* addr)
{
    (void)addr;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_stop_voice_recognition(bt_address_t* addr)
{
    (void)addr;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_phone_state_change(bt_address_t* addr, uint8_t num_active,
    uint8_t num_held, hfp_ag_call_state_t call_state,
    hfp_call_addrtype_t type, const char* number,
    const char* name)
{
    (void)addr;
    (void)num_active;
    (void)num_held;
    (void)call_state;
    (void)type;
    (void)number;
    (void)name;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_cind_response(bt_address_t* addr, hfp_ag_cind_resopnse_t* response)
{
    (void)addr;
    (void)response;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_clcc_response(bt_address_t* addr, uint32_t index,
    hfp_call_direction_t dir, hfp_ag_call_state_t call,
    hfp_call_mode_t mode, hfp_call_mpty_type_t mpty,
    hfp_call_addrtype_t type, const char* number)
{
    (void)addr;
    (void)index;
    (void)dir;
    (void)call;
    (void)mode;
    (void)mpty;
    (void)type;
    (void)number;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_dial_response(bt_address_t* addr, hfp_atcmd_result_t result)
{
    (void)addr;
    (void)result;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_cops_response(bt_address_t* addr, const char* operator_name, uint16_t length)
{
    (void)addr;
    (void)operator_name;
    (void)length;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_notify_device_status_changed(bt_address_t* addr,
    hfp_network_state_t network,
    hfp_roaming_state_t roam,
    uint8_t signal, uint8_t battery)
{
    (void)addr;
    (void)network;
    (void)roam;
    (void)signal;
    (void)battery;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_set_inband_ring_enable(bt_address_t* addr, bool enable)
{
    (void)addr;
    (void)enable;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_set_volume(bt_address_t* addr, hfp_volume_type_t type, uint8_t volume)
{
    (void)addr;
    (void)type;
    (void)volume;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_send_at_cmd(bt_address_t* addr, const char* atcmd, uint16_t length)
{
    (void)addr;
    (void)atcmd;
    (void)length;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_manufacture_id_response(bt_address_t* addr,
    const char* manufacturer_id,
    uint16_t length)
{
    (void)addr;
    (void)manufacturer_id;
    (void)length;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_model_id_response(bt_address_t* addr, const char* model_id, uint16_t length)
{
    (void)addr;
    (void)model_id;
    (void)length;
    return BT_STATUS_SUCCESS;
}

bt_status_t bt_sal_hfp_ag_error_response(bt_address_t* addr, hfp_atcmd_result_t result)
{
    (void)addr;
    (void)result;
    return BT_STATUS_SUCCESS;
}
