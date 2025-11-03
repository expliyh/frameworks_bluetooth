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

 #include "service_loop.h"
#include "sal_hfp_hf_interface.h"
#include "sal_connection_manager.h"
#include "sal_interface.h"
#include "sal_zblue.h"
#include "bt_debug.h"
#include <stdio.h>

#undef BT_UUID_DECLARE_16
#undef BT_UUID_DECLARE_32
#undef BT_UUID_DECLARE_128

#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/classic/hfp_hf.h>
#include <zephyr/bluetooth/classic/sdp.h>
#include <zephyr/bluetooth/l2cap.h>
#include <zephyr/net_buf.h>
#include <zephyr/sys/atomic.h>

static bt_list_t* pending_connections = NULL;

uint8_t on_sdp_done(struct bt_conn *conn, struct bt_sdp_client_result *result, const struct bt_sdp_discover_params *ignore);

NET_BUF_POOL_DEFINE(sdp_discover_pool, CONFIG_BT_MAX_CONN, BT_L2CAP_BUF_SIZE(CONFIG_BT_L2CAP_TX_MTU),
		    CONFIG_BT_CONN_TX_USER_DATA_SIZE, NULL);

static struct bt_sdp_discover_params sdp_discover = {
	.func = on_sdp_done,
	.pool = &sdp_discover_pool,
	.uuid = BT_UUID_DECLARE_16(BT_SDP_HANDSFREE_SVCLASS),
};

typedef struct _bt_hfp_hf_connection {
    bt_address_t* addr;
    struct bt_conn* conn;
    struct bt_hfp_hf_call *incoming_call;
    struct bt_list_t* held_calls;
    struct bt_list_t* active_calls;
    struct bt_hfp_hf *hf;
} bt_hfp_hf_connection_t;

static void free_connection(void* p_data)
{
    bt_hfp_hf_connection_t* data = (bt_hfp_hf_connection_t*)p_data;
    // bt_conn_unref(data->conn);
    free(data);
    return;
}

static bool mem_addr_cmp(void* p_data, void* context) {
    return p_data == context;
}

static bool sal_bt_hfp_hf_cmp(void* p_data, void* context) {
    bt_hfp_hf_connection_t* sal_conn = (bt_hfp_hf_connection_t*)p_data;
    struct bt_hfp_hf* hf = (struct bt_hfp_hf*)context;
    return sal_conn->hf == hf;
}

static bool sal_bt_addr_cmp(void* p_data, void* context) {
    bt_hfp_hf_connection_t* sal_conn = (bt_hfp_hf_connection_t*)p_data;
    bt_address_t* addr = (bt_address_t*)context;
    return !bt_addr_compare(sal_conn->addr, addr);
}

static bt_hfp_hf_connection_t* find_connection_by_addr(bt_address_t* addr) {
    return (bt_hfp_hf_connection_t*)bt_list_find(pending_connections, sal_bt_addr_cmp, addr);
}

static bt_hfp_hf_connection_t* find_connection_by_hf(struct bt_hfp_hf *hf) {
    return (bt_hfp_hf_connection_t*)bt_list_find(pending_connections, sal_bt_hfp_hf_cmp, hf);
}

static bt_hfp_hf_connection_t* find_connection_by_conn(struct bt_conn* conn) {
    return (bt_hfp_hf_connection_t*)bt_list_find(pending_connections, mem_addr_cmp, conn);
}

static void cmp_connection(void* p_data, void* context) {
    bt_hfp_hf_connection_t* data = (bt_hfp_hf_connection_t*)p_data;
    struct bt_hfp_hf *hf = (struct bt_hfp_hf*)context;
    if (data->hf == hf) {
        BT_LOGD("%s, HFP HF connected callback for pending connection", __func__);
    }
}

bt_status_t do_hf_connect(struct bt_conn *conn, uint16_t channel) {
    struct bt_hfp_hf *hf = NULL;
    
    if(bt_hfp_hf_connect(conn, &hf, channel)){
        BT_LOGE("%s, Failed to initiate HFP HF connection", __func__);
        bt_conn_unref(conn);
        return BT_STATUS_FAIL;
    }

    bt_hfp_hf_connection_t* new_connection = (bt_hfp_hf_connection_t*)zalloc(sizeof(bt_hfp_hf_connection_t));
    if (new_connection == NULL) {
        BT_LOGE("%s, Failed to allocate memory for new HFP HF connection", __func__);
        return BT_STATUS_FAIL;
    }
    bt_sal_get_remote_address(conn, new_connection->addr);
    new_connection->conn = conn;
    new_connection->hf = hf;
    new_connection->incoming_call = NULL;

    bt_list_add_tail(pending_connections, new_connection);

    return BT_STATUS_SUCCESS;
}

uint8_t on_sdp_done(struct bt_conn *conn, struct bt_sdp_client_result *result, const struct bt_sdp_discover_params *ignore)
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
            do_hf_connect(conn, value);
            if (err != 0) {
                BT_LOGD("Fail to create hfp AG connection (err %d)", err);
            }
        }
    }
    return BT_SDP_DISCOVER_UUID_STOP;
}

// TODO: Error Processing
static void on_hfp_hf_connected(struct bt_conn *conn, struct bt_hfp_hf *hf)
{
    BT_LOGD("%s, HFP HF connected, hf=%d", __func__, hf);
    // pending_connection--;
    bt_address_t bd_addr;
    if (bt_sal_get_remote_address(conn, &bd_addr) != BT_STATUS_SUCCESS)
        return;
    if (!find_connection_by_addr(&bd_addr)) {
            bt_hfp_hf_connection_t* new_connection = (bt_hfp_hf_connection_t*)zalloc(sizeof(bt_hfp_hf_connection_t));
            new_connection->addr = (bt_address_t*)zalloc(sizeof(bt_address_t));
        if (new_connection == NULL) {
            BT_LOGE("%s, Failed to allocate memory for new HFP HF connection", __func__);
            return BT_STATUS_FAIL;
        }
        bt_sal_get_remote_address(conn, new_connection->addr);
        new_connection->conn = conn;
        new_connection->hf = hf;
        new_connection->incoming_call = NULL;

        bt_list_add_tail(pending_connections, new_connection);
    }
    hfp_hf_on_connection_state_changed(&bd_addr, PROFILE_STATE_CONNECTING, 0, 0);
    hfp_hf_on_connection_state_changed(&bd_addr, PROFILE_STATE_CONNECTED, 0, 0);
}

static void hfp_hf_on_incoming_call(struct bt_hfp_hf *hf, struct bt_hfp_hf_call *call)
{
    bt_address_t *bd_addr = zalloc(sizeof(bt_address_t));

    BT_LOGD("%s, HFP HF incoming call, hf=%d", __func__, hf);

    bt_hfp_hf_connection_t* conn = find_connection_by_hf(hf);
    if (!conn) {
        BT_LOGE("%s, Failed to find connection", __func__);
        return;
    }
    bt_sal_get_remote_address(conn->conn, bd_addr);
    hfp_hf_on_call_setup_state_changed(bd_addr, 1);
}

static void zblue_on_subscriber_number(struct bt_hfp_hf *hf, const char *number, uint8_t type, uint8_t service)
{
    bt_address_t *bd_addr = zalloc(sizeof(bt_address_t));

    BT_LOGD("%s, HFP HF subscriber number, hf=%d", __func__, hf);

    bt_hfp_hf_connection_t* conn = find_connection_by_hf(hf);
    if (!conn) {
        BT_LOGE("%s, Failed to find connection", __func__);
        return;
    }

    hfp_subscriber_number_service_t fw_service = 0;
    switch (service)
    {
    case 4:
        fw_service = HFP_HF_SERVICE_VOICE;
        break;
    case 5:
        fw_service = HFP_HF_SERVICE_FAX;
        break;
    default:
        BT_LOGW("%s, Unknown service: %d", __func__, service);
        break;
    }

    bt_sal_get_remote_address(conn->conn, bd_addr);
    hfp_hf_on_subscriber_number_response(bd_addr, number, fw_service);
}

static struct bt_hfp_hf_cb hf_callbacks = {
    .connected = on_hfp_hf_connected,
    .disconnected = NULL,
    .sco_connected = NULL,
    .sco_disconnected = NULL,
    .service = NULL,
    .outgoing = NULL,
    .remote_ringing = NULL,
    .incoming = hfp_hf_on_incoming_call,
    .incoming_held = NULL,
    .accept = NULL,
    .reject = NULL,
    .terminate = NULL,
    .held = NULL,
    .retrieve = NULL,
    .signal = NULL,
    .roam = NULL,
    .battery = NULL,
    .ring_indication = NULL,
    .dialing = NULL,
    .clip = NULL,
    .vgm = NULL,
    .vgs = NULL,
    .inband_ring = NULL,
    .operator = NULL,
    .codec_negotiate = NULL,
    .ecnr_turn_off = NULL,
    .call_waiting = NULL,
    .voice_recognition = NULL,
    .vre_state = NULL,
    .textual_representation = NULL,
    .request_phone_number = NULL,
    .subscriber_number = zblue_on_subscriber_number,
};

bt_status_t bt_sal_hfp_hf_init(uint32_t hf_features, uint8_t p_max_connection)
{
    // max_connection = p_max_connection;
    // available_connections = bt_list_new(free_connection);
    pending_connections = bt_list_new(free_connection);
    SAL_CHECK_RET(bt_hfp_hf_register(&hf_callbacks), 0);
    return BT_STATUS_SUCCESS;
}

void bt_sal_hfp_hf_cleanup(void)
{
    printf("bt_sal_hfp_hf_cleanup: Currently not supported\n");
}

bt_status_t pre_hfp_hf_connect()
{
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_connect(bt_address_t* addr)
{
    struct bt_conn* conn = bt_conn_lookup_addr_br(addr);

    if (!conn){
        BT_LOGW("%s, acl not conneted, try connect\n", __func__);
        if (bt_sal_connect(0, addr) != BT_STATUS_SUCCESS)
            return BT_STATUS_FAIL;
        conn = bt_conn_lookup_addr_br(addr);
    }

    SAL_CHECK_RET(bt_sdp_discover(conn, &sdp_discover), 0);

    return BT_STATUS_SUCCESS;
}


bt_status_t bt_sal_hfp_hf_disconnect(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_disconnect: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_connect_audio(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_connect_audio: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_disconnect_audio(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_disconnect_audio: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_answer_call(bt_address_t* addr)
{
    bt_hfp_hf_connection_t* sal_conn = find_connection_by_addr(addr);
    SAL_CHECK_RET(bt_hfp_hf_accept(sal_conn->incoming_call), 0);
    return BT_STATUS_FAIL;
}

bt_status_t bt_sal_hfp_hf_reject_call(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_reject_call: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_hold_call(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_hold_call: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_hangup_call(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_hangup_call: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_dial_number(bt_address_t* addr, const char* number)
{
    printf("bt_sal_hfp_hf_dial_number: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_dial_memory(bt_address_t* addr, uint32_t memory)
{
    printf("bt_sal_hfp_hf_dial_memory: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_call_control(bt_address_t* addr, hfp_call_control_t chld, uint32_t index)
{
    printf("bt_sal_hfp_hf_call_control: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_get_current_calls(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_get_current_calls: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_set_volume(bt_address_t* addr, hfp_volume_type_t type, uint8_t volume)
{
    printf("bt_sal_hfp_hf_set_volume: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_start_voice_recognition(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_start_voice_recognition: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_stop_voice_recognition(bt_address_t* addr)
{
    printf("bt_sal_hfp_hf_stop_voice_recognition: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_send_battery_level(bt_address_t* addr, uint8_t value)
{
    printf("bt_sal_hfp_hf_send_battery_level: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_send_at_cmd(bt_address_t* addr, const char* cmd, uint16_t len)
{
    printf("bt_sal_hfp_hf_send_at_cmd: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}

bt_status_t bt_sal_hfp_hf_send_dtmf(bt_address_t* addr, char dtmf)
{
    printf("bt_sal_hfp_hf_send_dtmf: Currently not supported\n");
    return BT_STATUS_UNSUPPORTED;
}


bt_status_t bt_sal_hfp_hf_get_subscriber_number(bt_address_t* addr)
{
    bt_hfp_hf_connection_t* sal_conn = find_connection_by_addr(addr);
    SAL_CHECK_RET(bt_hfp_hf_query_subscriber(sal_conn->hf), 0);
    return BT_STATUS_SUCCESS;
}
