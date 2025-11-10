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
#include "sal_connection_manager.h"
#include "sal_hfp_internal.h"
#include "sal_interface.h"
#include "sal_zblue.h"
#include "bt_debug.h"
#include <zephyr/bluetooth/classic/hfp_ag.h>
#include <zephyr/bluetooth/classic/sdp.h>
#include <zephyr/logging/log.h>
#include <zephyr/bluetooth/l2cap.h>
#include <zephyr/net_buf.h>
#include <zephyr/sys/atomic.h>

static bt_list_t* pending_connections = NULL;

LOG_MODULE_REGISTER(sal_hfp_ag, CONFIG_BT_HFP_AG_LOG_LEVEL);

static uint8_t on_sdp_done(struct bt_conn *conn, struct bt_sdp_client_result *result, const struct bt_sdp_discover_params *ignore);

NET_BUF_POOL_DEFINE(ag_sdp_discover_pool, CONFIG_BT_MAX_CONN, BT_L2CAP_BUF_SIZE(CONFIG_BT_L2CAP_TX_MTU),
		    CONFIG_BT_CONN_TX_USER_DATA_SIZE, NULL);

static struct bt_sdp_discover_params sdp_discover = {
	.func = on_sdp_done,
	.pool = &ag_sdp_discover_pool,
	.uuid = BT_UUID_DECLARE_16(BT_SDP_HANDSFREE_AGW_SVCLASS),
};

typedef struct _bt_hfp_ag_call {
	char number[20];
	hfp_ag_call_state_t state;
    struct bt_hfp_ag_call* session;
} bt_hfp_ag_call_t;

typedef struct _bt_hfp_ag_connection {
    bt_address_t* addr;
    struct bt_conn* conn;
    struct bt_hfp_ag *ag;
    struct bt_list_t* calls;
} bt_hfp_ag_connection_t;

static void free_connection(void* p_data)
{
    bt_hfp_ag_connection_t* data = (bt_hfp_ag_connection_t*)p_data;
    // bt_conn_unref(data->conn);
    free(data);
    return;
}

static void free_call(void* p_data)
{
    bt_hfp_ag_call_t* data = (bt_hfp_ag_call_t*)p_data;
    free(data);
    return;
}

static bool mem_addr_cmp(void* p_data, void* context) {
    return p_data == context;
}

static bool sal_bt_hfp_ag_call_number_cmp(void* p_data, void* context) {
    bt_hfp_ag_call_t* sal_call = (bt_hfp_ag_call_t*)p_data;
    char* number = (char*)context;
    return !strcmp(sal_call->number, number);
}

static bool sal_bt_hfp_ag_cmp(void* p_data, void* context) {
    bt_hfp_ag_connection_t* sal_conn = (bt_hfp_ag_connection_t*)p_data;
    struct bt_hfp_ag* ag = (struct bt_hfp_ag*)context;
    return sal_conn->ag == ag;
}

static bool sal_bt_addr_cmp(void* p_data, void* context) {
    bt_hfp_ag_connection_t* sal_conn = (bt_hfp_ag_connection_t*)p_data;
    bt_address_t* addr = (bt_address_t*)context;
    return !bt_addr_compare(sal_conn->addr, addr);
}

static bt_hfp_ag_call_t* find_call_by_number(bt_hfp_ag_connection_t conn, const char* number) {
    if (!on_going_calls) {
        BT_LOGE("%s, on_going_calls is NULL", __func__);
    }
    bt_list_t* on_going_calls = conn.calls;
    return (bt_hfp_ag_call_t*)bt_list_find(on_going_calls, sal_bt_hfp_ag_call_number_cmp, (void*)number);
}

static bt_hfp_ag_connection_t* find_connection_by_addr(bt_address_t* addr) {
    if (!pending_connections) {
        BT_LOGE("%s, pending_connections is NULL", __func__);
    }
    return (bt_hfp_ag_connection_t*)bt_list_find(pending_connections, sal_bt_addr_cmp, addr);
}

static bt_hfp_ag_connection_t* find_connection_by_ag(struct bt_hfp_ag *ag) {
    return (bt_hfp_ag_connection_t*)bt_list_find(pending_connections, sal_bt_hfp_ag_cmp, ag);
}

static bt_hfp_ag_connection_t* find_connection_by_conn(struct bt_conn* conn) {
    return (bt_hfp_ag_connection_t*)bt_list_find(pending_connections, mem_addr_cmp, conn);
}

static void cmp_connection(void* p_data, void* context) {
    bt_hfp_ag_connection_t* data = (bt_hfp_ag_connection_t*)p_data;
    struct bt_hfp_ag *ag = (struct bt_hfp_ag*)context;
    if (data->ag == ag) {
        BT_LOGD("%s, HFP AG connected callback for pending connection", __func__);
    }
}

static void ag_connected(struct bt_conn *conn, struct bt_hfp_ag *ag)
{
    BT_LOGD("%s, HFP AG connected, ag=%d", __func__, ag);
    bt_address_t bd_addr;
    if (bt_sal_get_remote_address(conn, &bd_addr) != BT_STATUS_SUCCESS) {
        BT_LOGE("%s, Failed to get remote address", __func__);
        return;
    }
    if (!find_connection_by_addr(&bd_addr)) {
            bt_hfp_ag_connection_t* new_connection = (bt_hfp_ag_connection_t*)zalloc(sizeof(bt_hfp_ag_connection_t));
            new_connection->addr = (bt_address_t*)zalloc(sizeof(bt_address_t));
        if (new_connection == NULL) {
            BT_LOGE("%s, Failed to allocate memory for new HFP HF connection", __func__);
            return BT_STATUS_FAIL;
        }
        bt_sal_get_remote_address(conn, new_connection->addr);
        new_connection->conn = conn;
        new_connection->ag = ag;

        bt_list_add_tail(pending_connections, new_connection);
    }
    hfp_ag_on_connection_state_changed(&bd_addr, PROFILE_STATE_CONNECTING, 0, 0);
    hfp_ag_on_connection_state_changed(&bd_addr, PROFILE_STATE_CONNECTED, 0, 0);
}

static void ag_disconnected(struct bt_hfp_ag *ag)
{
    bt_hfp_ag_connection_t* conn = find_connection_by_ag(ag);
    if (!conn) {
        BT_LOGE("%s, Failed to find connection", __func__);
        return;
    }
    bt_address_t *bd_addr = conn->addr;

    hfp_ag_on_connection_state_changed(bd_addr, PROFILE_STATE_DISCONNECTING, 0, 0);
    hfp_ag_on_connection_state_changed(bd_addr, PROFILE_STATE_DISCONNECTED, 0, 0);

    bt_list_remove(pending_connections, conn);
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

typedef struct _do_ag_connect_params {
    struct bt_conn *conn;
    uint16_t channel;
} do_ag_connect_params_t;

static void do_ag_connect(do_ag_connect_params_t *params)
{
    struct bt_conn *conn = params->conn;
    uint16_t channel = params->channel;
    free(params);
    struct bt_hfp_ag *ag = NULL;

    if (z_bt_hfp_ag_connect(conn, &ag, channel)) {
        BT_LOGE("%s, Failed to initiate HFP HF connection", __func__);
        bt_conn_unref(conn);
        return;
    }

    bt_hfp_ag_connection_t *new_connection =
        (bt_hfp_ag_connection_t *)zalloc(sizeof(bt_hfp_ag_connection_t));
    if (!new_connection) {
        BT_LOGE("%s, Failed to allocate memory for new HFP HF connection", __func__);
        return;
    }

    bt_sal_get_remote_address(conn, new_connection->addr);
    new_connection->conn = conn;
    new_connection->ag = ag;

    bt_list_add_tail(pending_connections, new_connection);

    BT_LOGI("%s, HFP AG connection established successfully", __func__);
}

static uint8_t on_sdp_done(struct bt_conn *conn, struct bt_sdp_client_result *result, const struct bt_sdp_discover_params *ignore)
{
    int err;
    uint16_t value;

    BT_LOGD("Discover done");

    if (result->resp_buf != NULL) {
        err = bt_sdp_get_proto_param(result->resp_buf, BT_SDP_PROTO_RFCOMM, &value);

        if (err != 0) {
            BT_LOGD("Fail to parser RFCOMM the SDP response!");
        } else {
            BT_LOGD("The server channel is %d", value);
            err = 0;
            do_ag_connect_params_t* params = (do_ag_connect_params_t*)zalloc(sizeof(do_ag_connect_params_t));
            if (params == NULL) {
                BT_LOGE("%s, Failed to allocate memory for new HFP HF connection", __func__);
                return -1;
            }
            params->conn = conn;
            params->channel = value;
            CALL_IN_SERVICE(do_ag_connect, params);
            params = NULL;
            if (err != 0) {
                BT_LOGD("Fail to create hfp connection (err %d)", err);
            }
        }
    }
    return BT_SDP_DISCOVER_UUID_STOP;
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
    BT_LOGD("%s, HFP AG init", __func__);
    pending_connections = bt_list_new(free_connection);
    on_going_calls = bt_list_new(free_call);

    bt_hfp_ag_register(&g_hfp_ag_cb);
    return BT_STATUS_SUCCESS;
}

void bt_sal_hfp_ag_cleanup(void)
{
}

bt_status_t bt_sal_hfp_ag_connect(bt_address_t* addr)
{
    struct bt_conn* conn = bt_conn_lookup_addr_br((bt_addr_t*)addr);

    if (!conn){
        BT_LOGW("%s, acl not conneted, try connect\n", __func__);
        if (bt_sal_connect(0, addr) != BT_STATUS_SUCCESS)
            return BT_STATUS_FAIL;
        conn = bt_conn_lookup_addr_br((bt_addr_t*)addr);
        if (!conn) {
            BT_LOGE("%s, acl not conneted, try connect failed\n", __func__);
            return BT_STATUS_FAIL;
        }
    }

    SAL_CHECK_RET(bt_sdp_discover(conn, &sdp_discover), 0);

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
