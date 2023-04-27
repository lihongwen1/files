package com.sz.verefa.sweeprobot.module.lds;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.hjq.toast.ToastUtils;
import com.ldrobot.base_library.api.LDHideActiveArea;
import com.ldrobot.base_library.api.LDMapCase;
import com.ldrobot.base_library.api.LDMapType;
import com.ldrobot.base_library.api.LDPathType;
import com.ldrobot.base_library.api.LDShowCase;
import com.ldrobot.base_library.api.OnLDForbidAreaSetInRangeListener;
import com.ldrobot.base_library.api.OnLDSweepMapViewListener;
import com.ldrobot.base_library.common.LDMapView;
import com.ldrobot.bean.LDArea;
import com.ldrobot.bean.LDObstacle;
import com.ldrobot.bean.LDSweepMap;
import com.ldrobot.bean.SweepPath;
import com.sineva.SafeHandler;
import com.sineva.bar.OnTitleBarListener;
import com.sineva.utils.AppManager;
import com.sineva.utils.utilcode.util.ClickUtils;
import com.sineva.utils.utilcode.util.LogUtils;
import com.sineva.utils.utilcode.util.ThreadUtils;
import com.sineva.utils.utilcode.util.time.CountdownUtil;
import com.sz.verefa.sweeprobot.LdsMainActivity;
import com.sz.verefa.sweeprobot.R;
import com.sz.verefa.sweeprobot.activity.BaseActivity;
import com.sz.verefa.sweeprobot.base.CommonListener;
import com.sz.verefa.sweeprobot.constants.LdsConstant;
import com.sz.verefa.sweeprobot.constants.SetConstant;
import com.sz.verefa.sweeprobot.databinding.ActivityLdsPartCleanBinding;
import com.sz.verefa.sweeprobot.event.Event;
import com.sz.verefa.sweeprobot.event.PartCleanMsg;
import com.sz.verefa.sweeprobot.module.lds.bean.MapData;
import com.sz.verefa.sweeprobot.module.lds.manager.t4.T4SweepMapParseUtil;
import com.sz.verefa.sweeprobot.module.lds.manager.t4.T4SweepPathParseUtil;
import com.sz.verefa.sweeprobot.module.lds.mvp.mapedit.LdsMapEditModel;
import com.sz.verefa.sweeprobot.tuyaview.TuyaApplication;
import com.sz.verefa.sweeprobot.utils.Utils;
import com.tuya.smart.home.sdk.TuyaHomeSdk;
import com.tuya.smart.sdk.api.IDevListener;
import com.tuya.smart.sdk.api.IResultCallback;
import com.tuya.smart.sdk.api.ITuyaDevice;
import com.tuya.smart.sdk.bean.DeviceBean;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Description:激光扫地机指哪扫哪和局部清扫页
 * Create by lhw, at 2022/8/16
 */

public class LdsPartCleanActivity extends BaseActivity implements View.OnClickListener {
    private static final int MSG_1 = 1;
    private final CountdownUtil countdownUtil = CountdownUtil.newInstance();
    private ActivityLdsPartCleanBinding binding;
    private String devId;
    private ITuyaDevice mDevice;
    private DeviceBean mDeviceBean;
    private LDMapView ldMapView;
    private final SafeHandler handler = new SafeHandler(this, Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_1:
                    ldMapView.getMapDataSet().setMapType(LDMapType.TapPoint);
                    EventBus.getDefault().unregister(this);
                    break;
            }
            return false;
        }
    });
    private String clean_mode;
    private String robot_state;
    private String command_trans;
    private String mCurForbidWallData;
    private int mCurPathId;   //当前路径id
    private String mCurForbidRoomData;
    private String mCurZoneAreaData;
    private LdsMapEditModel model;
    private final IDevListener listener = new IDevListener() {
        @Override
        public void onDpUpdate(String devId, String dpStr) {
            update(dpStr);
        }

        @Override
        public void onRemoved(String devId) {

        }

        @Override
        public void onStatusChanged(String devId, boolean online) {

        }

        @Override
        public void onNetworkStatusChanged(String devId, boolean status) {

        }

        @Override
        public void onDevInfoUpdate(String devId) {

        }
    };
    private Map<String, Object> main_dps;
    private boolean isLoadMap;

    @Override
    protected int getContentView() {
        return R.layout.activity_lds_part_clean;
    }

    @Override
    protected void getInit() {
        initEventBus(true);
        initArgs(getIntent());
        initView();
        initMapConfig();
        initData();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEventMap(Event<Object> mapMsg) {
        LogUtils.e("局部清扫接收地图", mapMsg.getCode());
        if (mapMsg.getCode() == Event.CODE_LDSWEEPMAP) {
            //此处只接收地图数据
            try {
                if (mapMsg.getData() != null) {
//                    MapData data = (MapData) mapMsg.getData();
                    PartCleanMsg msg = (PartCleanMsg) mapMsg.getData();
                    T4SweepMapParseUtil t4SweepMapParseUtil = new T4SweepMapParseUtil();

                    LDSweepMap ldSweepMap = t4SweepMapParseUtil.parseSweepMap(msg.getMapStr());
                    ArrayList<LDArea> mAreaList = new ArrayList<>();
                    ArrayList<LDArea> ldAreas2 = model.parseT4ForbidArea(msg.getmForbidStr());
                    ArrayList<LDArea> ldAreas3 = model.parseT4VirtualWall(msg.getmWallStr());
                    mAreaList.addAll(ldAreas2);
                    mAreaList.addAll(ldAreas3);
                    ldSweepMap.setArea(mAreaList);
                    ldMapView.getMapDataSet().loadMap(ldSweepMap);
                    if (!isLoadMap) {
                        dismiss();
                        isLoadMap = true;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showNoMapState(false);
                            }
                        });
                        handler.sendEmptyMessageDelayed(MSG_1, 10);  //自动添加目标点
                    }
                }

            } catch (NullPointerException e) {
                LogUtils.e("地图为空");
                showNoMapState(true);
            }
        }
    }


    private void initArgs(Intent intent) {
        if (devId != null && devId.equals(intent.getStringExtra(SetConstant.KEY_DEVICE_ID))) return;
        devId = intent.getStringExtra(SetConstant.KEY_DEVICE_ID);
        if (devId != null && !devId.isEmpty()) {
            LogUtils.e("进入局部清扫界面");
            mDevice = TuyaHomeSdk.newDeviceInstance(devId);
            mDevice.registerDevListener(listener);  //注册设备监听,首次建立监听或上报所有的状态，此时初始化
            mDeviceBean = Utils.getDevices(devId);
            robot_state = (String) mDeviceBean.getDps().get(LdsConstant.DP_CODE.DP_ROBOT_STATE);
        }
        model = new LdsMapEditModel();
    }

    private void initView() {
        binding = ActivityLdsPartCleanBinding.bind(getView());
        ClickUtils.applyGlobalDebouncing(binding.tvPartClean, 500, this);
        ClickUtils.applyGlobalDebouncing(binding.tvGoHere, 500, this);
        binding.titlebar.setOnTitleBarListener(new OnTitleBarListener() {
            @Override
            public void onLeftClick(View v) {
                goBack();
            }

            @Override
            public void onTitleClick(View v) {

            }

            @Override
            public void onRightClick(View v) {

            }

            @Override
            public void onRightImageClick(View v) {

            }
        });
    }

    private void initMapConfig() {
        //创建地图对象
        ldMapView = new LDMapView(this);
//        ldMapView.getMapView().setZOrderOnTop(true);
//        ldMapView.getMapView().getHolder().setFormat(PixelFormat.TRANSLUCENT);
        //将地图对象添加UI上
        binding.partMapLayout.addView(ldMapView.getMapView());
        ldMapView.getMapConfig().showDefaultRobot(false);
        ldMapView.getMapDataSet().setMapType(LDMapType.TapPoint);
        //设置地图颜色【未分区颜色】
        ldMapView.getMapConfig().setMapColors(LdsConstant.BG_COLOR, LdsConstant.WALL_COLOR, LdsConstant.IN_COLOR);
        //设置路径颜色
        ldMapView.getMapConfig().setMapPathColor(LdsConstant.PATH_COLOR);
        //设置地图方案/个性化样式，默认是TypeOne
        ldMapView.getMapConfig().setMapCaseType(LDMapCase.TypeTwo);

        ldMapView.getMapConfig().setMapRobotIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.map_robot));
        //设置充电座图标
        ldMapView.getMapConfig().setMapChargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.charging_base));
        //设置分区颜色
        ldMapView.getMapConfig().setRoomColors(new int[]{
                        getResources().getColor(R.color.color_normal1),
                        getResources().getColor(R.color.color_normal2),
                        getResources().getColor(R.color.color_normal3),
                        getResources().getColor(R.color.color_normal4),
                        getResources().getColor(R.color.color_normal5),
                        getResources().getColor(R.color.color_normal6),
                        getResources().getColor(R.color.color_normal7),
                        getResources().getColor(R.color.color_normal8),
                        getResources().getColor(R.color.color_normal9),
                        getResources().getColor(R.color.color_normal10)
                }
                ,
                new int[]{
                        getResources().getColor(R.color.color_select1),
                        getResources().getColor(R.color.color_select2),
                        getResources().getColor(R.color.color_select3),
                        getResources().getColor(R.color.color_select4),
                        getResources().getColor(R.color.color_select5),
                        getResources().getColor(R.color.color_select6),
                        getResources().getColor(R.color.color_select7),
                        getResources().getColor(R.color.color_select8),
                        getResources().getColor(R.color.color_select9),
                        getResources().getColor(R.color.color_select10)
                });

        //初始化一些地图图标配置
        initPeizhi();

        //设置虚拟墙拖拽图标
        ldMapView.getMapConfig().setVirtualWallDragIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.map_editormagnify));
        //设置虚拟墙删除图标
        ldMapView.getMapConfig().setVirtualWallDelIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.map_editor_close));

        //设置禁区拖拽图标
        ldMapView.getMapConfig().setForbidAreaDragIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.map_editormagnify));
        //设置禁区删除图标
        ldMapView.getMapConfig().setForbidAreaDeleteIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.map_editor_close));

        // <color name="tanslantion">#00000000</color>
        ldMapView.getMapConfig().setPointAreaSolidColor(getResources().getColor(R.color.transparent));
        ldMapView.getMapConfig().setPointAreaStrokeColor(getResources().getColor(R.color.transparent));
        ldMapView.getMapConfig().setPointAreaTextColor(getResources().getColor(R.color.transparent));
        ldMapView.getMapConfig().setTapPointBitmap(BitmapFactory.decodeResource(getResources(), R.mipmap.map_coord));
        ldMapView.getMapConfig().setShowTapPointAnimator(false);
        //设置隐藏路径类型
        ArrayList<LDPathType> pathTypes = new ArrayList<>();
        pathTypes.add(LDPathType.BackChargePtah);
        pathTypes.add(LDPathType.ControlPath);
        pathTypes.add(LDPathType.InnerAreaPath);
        pathTypes.add(LDPathType.BetweenAreaPath);
        pathTypes.add(LDPathType.ErrorPath);
        ldMapView.getMapDataSet().setHidePathTypes(pathTypes);

        //设置分区选择时，勾选图标
        ldMapView.getMapConfig().setRoomSelectedIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.select_gou_icon));
        //设置激活区域隐藏，默认都不隐藏
        ldMapView.getMapDataSet().setHideActiveAreaType(LDHideActiveArea.Area);

        initMapEditListener();
    }

    private void initPeizhi() {
        //设置需要显示的个性化类型
        ldMapView.getMapConfig().setRoomShowCase(LDShowCase.CleanTime, LDShowCase.Fan, LDShowCase.Water);

        //设置未选中时，清扫次数 挡位和挡位对应的图标
        HashMap<Integer, Bitmap> cleanTimeNormalIcons = new HashMap<>();
        cleanTimeNormalIcons.put(1, BitmapFactory.decodeResource(getResources(), R.mipmap.clean_time_normal_normal));
        cleanTimeNormalIcons.put(2, BitmapFactory.decodeResource(getResources(), R.mipmap.clean_time_depth_normal));
        ldMapView.getMapConfig().setMapCleanTimesNormalIcon(cleanTimeNormalIcons);

        //设置选中时，清扫次数 挡位和挡位对应的图标
        HashMap<Integer, Bitmap> cleanTimeSelectIcons = new HashMap<>();
        cleanTimeSelectIcons.put(1, BitmapFactory.decodeResource(getResources(), R.mipmap.clean_time_normal_selected));
        cleanTimeSelectIcons.put(2, BitmapFactory.decodeResource(getResources(), R.mipmap.clean_time_depth_selected));
        ldMapView.getMapConfig().setMapCleanTimesSelectIcon(cleanTimeSelectIcons);

        //设置未选中时，水量 挡位和挡位对应的图标
        HashMap<Integer, Bitmap> waterLevelNormalIcons = new HashMap<>();
        waterLevelNormalIcons.put(1, BitmapFactory.decodeResource(getResources(), R.mipmap.water_one_normal));
        waterLevelNormalIcons.put(2, BitmapFactory.decodeResource(getResources(), R.mipmap.water_two_normal));
        waterLevelNormalIcons.put(3, BitmapFactory.decodeResource(getResources(), R.mipmap.water_three_normal));
        ldMapView.getMapConfig().setMapWaterLevelNormalIcon(waterLevelNormalIcons);

        //设置选中时，水量 挡位和挡位对应的图标
        HashMap<Integer, Bitmap> waterLevelSelectIcons = new HashMap<>();
        waterLevelSelectIcons.put(1, BitmapFactory.decodeResource(getResources(), R.mipmap.water_one_selected));
        waterLevelSelectIcons.put(2, BitmapFactory.decodeResource(getResources(), R.mipmap.water_two_selected));
        waterLevelSelectIcons.put(3, BitmapFactory.decodeResource(getResources(), R.mipmap.water_three_selected));
        ldMapView.getMapConfig().setMapWaterLevelSelectIcon(waterLevelSelectIcons);

        //设置未选中时，吸力 挡位和挡位对应的图标
        HashMap<Integer, Bitmap> fanLevelNormalIcons = new HashMap<>();
        fanLevelNormalIcons.put(1, BitmapFactory.decodeResource(getResources(), R.mipmap.fan_one_normal));
        fanLevelNormalIcons.put(2, BitmapFactory.decodeResource(getResources(), R.mipmap.fan_two_normal));
        fanLevelNormalIcons.put(3, BitmapFactory.decodeResource(getResources(), R.mipmap.fan_three_normal));
        ldMapView.getMapConfig().setMapFansLevelNormalIcon(fanLevelNormalIcons);

        //设置选中时，吸力 挡位和挡位对应的图标
        HashMap<Integer, Bitmap> fanLevelSelectIcons = new HashMap<>();
        fanLevelSelectIcons.put(1, BitmapFactory.decodeResource(getResources(), R.mipmap.fan_one_selected));
        fanLevelSelectIcons.put(2, BitmapFactory.decodeResource(getResources(), R.mipmap.fan_two_selected));
        fanLevelSelectIcons.put(3, BitmapFactory.decodeResource(getResources(), R.mipmap.fan_three_selected));
        ldMapView.getMapConfig().setMapFansLevelSelectIcon(fanLevelSelectIcons);
    }

    /**
     * 设置监听
     */
    private void initMapEditListener() {
        ldMapView.getMapDataSet().setOnLDSweepMapViewListener(new OnLDSweepMapViewListener() {

            @Override
            public void surfaceCreated() {
                LogUtils.e("surfaceCreated");
            }

            @Override
            public void onSurfaceDestroy() {
                LogUtils.e("onSurfaceDestroy");
            }

            @Override
            public void clickSweepArea(LDArea LdArea, boolean isAdd) {
                //当前选中区域
                LogUtils.e("clickSweepArea");
            }

            @Override
            public void sweepMapCreated() {
                LogUtils.e("sweepMapCreated");
            }

            @Override
            public void onSetMapComplete() {
                LogUtils.e("onSetMapComplete");
            }

            @Override
            public void changeSelectAreaState() {
                LogUtils.e("changeSelectAreaState");
            }

            @Override
            public void onDeleteSweepArea() {
                LogUtils.e("onDeleteSweepArea");
            }

            @Override
            public void autoClickSweepArea(int autoId, boolean isSelect) {
                LogUtils.e("autoClickSweepArea");
            }

            @Override
            public void onClickMapOutSide() {
                LogUtils.e("onClickMapOutSide");
            }

            @Override
            public void onError(int code) {
                LogUtils.e("onError");
            }

            @Override
            public void onTouchSmallObstacle(LDObstacle ldObstacle) {

            }

            @Override
            public void onTouchBigObstacle(LDObstacle ldObstacle) {

            }
        });
        //设置禁区及虚拟墙设置  充电座 限制范围 监听
        ldMapView.getMapDataSet().setOnLDForbidAreaSetInRangeListener(new OnLDForbidAreaSetInRangeListener() {
            @Override
            public void onOutOfRange(boolean flag) {
                if (!flag) {
                    Log.i("MapDemo", "禁区不符合标准，在充电座限制范围内，自动弹开");
                    ldMapView.getMapDataSet().bounceForbidArea();
                } else {
                    Log.i("MapDemo", "禁区符合标准");
                }
            }
        });
    }

    private void initData() {
        //请求一次地图信息
        displayLoading();
        Utils.sendDpCmd(mDevice, LdsConstant.DP_CODE.DP_REQUEST, LdsConstant.GET_BOTH, new IResultCallback() {
            @Override
            public void onError(String code, String error) {

            }

            @Override
            public void onSuccess() {

            }
        });
        main_dps = mDeviceBean.getDps();

        robot_state = (String) main_dps.get(LdsConstant.DP_CODE.DP_ROBOT_STATE);
        LogUtils.e("robot_state-----" + robot_state);
//        if (robot_state != null) {
//            updatePage();
//        }
        if (TuyaApplication.mCurMapDataStr != null) {
            dealP2pMapData(TuyaApplication.mCurMapDataStr);
        } else {
            showNoMapState(true);
        }
    }

    public void showNoMapState(boolean showEmpty) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.noMapImg.setVisibility(showEmpty ? View.VISIBLE : View.GONE);

//                binding.tvMsgTips.setText(showEmpty ? getString(R.string.nomap) : getString(R.string.please_select_goal_point));
                binding.partMapLayout.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
                binding.tvMsgTips.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
            }
        });
    }


    private void update(String dpStr) {
        LogUtils.e(dpStr);
        try {
            JSONObject jsonObject = new JSONObject(dpStr);
            Iterator<String> keys = jsonObject.keys();
            for (Iterator<String> it = keys; it.hasNext(); ) {
                String s = it.next();
                switch (s) {
                    case LdsConstant.DP_CODE.DP_CLEAN_MODE:
                        clean_mode = jsonObject.getString(LdsConstant.DP_CODE.DP_CLEAN_MODE);
                        LogUtils.e("LdsMainActivity:JSON:GET4:" + clean_mode);
                        break;
                    case LdsConstant.DP_CODE.DP_ROBOT_STATE:
                        robot_state = jsonObject.getString(LdsConstant.DP_CODE.DP_ROBOT_STATE);
                        LogUtils.e("LdsMainActivity:JSON:GET5:" + robot_state);
//                        if (robot_state != null) {
//                            updatePage();
//                        }
                        break;

                    case LdsConstant.DP_CODE.DP_COMMAND_TRANS:
                        command_trans = jsonObject.getString(LdsConstant.DP_CODE.DP_COMMAND_TRANS);
                        initCommandTrans(command_trans);
                        break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据当前状态更新页面，按钮状态切换
     */
    private void updatePage() {
        switch (robot_state) {
            case LdsConstant.STATE_GOTO_POS:
                binding.tvMsgTips.setText(R.string.going_to_goal);
                Utils.changeTopDrawable(LdsPartCleanActivity.this, binding.tvGoHere, R.mipmap.go_here_pause);
                break;
            case LdsConstant.STATE_POS_ARRIVED:
                Utils.changeTopDrawable(LdsPartCleanActivity.this, binding.tvGoHere, R.mipmap.go_here);
                binding.tvMsgTips.setText(R.string.arrived_to_goal);
                break;
            case LdsConstant.STATE_POS_UNARRIVE:
                Utils.changeTopDrawable(LdsPartCleanActivity.this, binding.tvGoHere, R.mipmap.go_here);
                binding.tvMsgTips.setText(R.string.please_select_goal_point);
                break;
            default:
                Utils.changeTopDrawable(LdsPartCleanActivity.this, binding.tvGoHere, R.mipmap.go_here);
                binding.tvMsgTips.setText(R.string.please_select_goal_point);
                break;
        }
    }

    /**
     * 初始化加载虚拟信息到地图上显示，主要包括划区、禁区和虚拟墙列表
     *
     * @param command_trans
     */
    private void initCommandTrans(String command_trans) {
        String type = command_trans.substring(6, 8);
        switch (type) {
            case LdsConstant.POINT_WALL:
                mCurForbidWallData = command_trans;
                break;
            case LdsConstant.POINT_FORBID:
                mCurForbidRoomData = command_trans;
                break;
            case LdsConstant.POINT_ZONE:
                LogUtils.e("当前有划区清扫");
                mCurZoneAreaData = command_trans;
                break;
            case LdsConstant.POINT_PART:
                LogUtils.e("定点执行反馈成功");
                dismiss();
                countdownUtil.stop();
                AppManager.instance.returnToActivity(LdsMainActivity.class);
//                updatePage();
                break;
        }
        LogUtils.e("统一设置区域信息");
        model.getAllAreaList(mCurZoneAreaData, mCurForbidRoomData, mCurForbidWallData, new CommonListener<ArrayList<LDArea>>() {
            @Override
            public void onResult(ArrayList<LDArea> ldAreas) {
//                ldMapView.getMapDataSet().setAreaInformation(ldAreas);
                LogUtils.e("统一设置区域信息=====" + ldAreas.size());
            }
        });
    }

    private void dealP2pMapData(String mMapDataStr) {
        LogUtils.e("禁区设置接受P2P地图数据回调成功---" + mMapDataStr);
        ThreadUtils.executeBySingle(new ThreadUtils.SimpleTask<MapData>() {
            @Override
            public MapData doInBackground() {
                T4SweepMapParseUtil t4SweepMapParseUtil = new T4SweepMapParseUtil();
                LDSweepMap ldSweepMap = t4SweepMapParseUtil.parseSweepMap(mMapDataStr);
                if (ldSweepMap != null) {
                    if (ldSweepMap.getWidth() == 0 || ldSweepMap.getHeight() == 0) {
                        return null;
                    }
                }
                MapData mapData = new MapData();
                mapData.setData(ldSweepMap);
                return mapData;
            }

            @Override
            public void onSuccess(MapData result) {
                if (result != null) {
                    //设置地图数据
                    ldMapView.getMapDataSet().loadMap(result.getData());
                    if (!isLoadMap) {
                        dismiss();
                        isLoadMap = true;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showNoMapState(false);
                            }
                        });
                        handler.sendEmptyMessageDelayed(MSG_1, 10);  //自动添加目标点
                    }
                } else {
                    dismiss();
                    showNoMapState(true);
                }
            }
        });
    }

    //处理P2P接收到的路径数据
    private void dealP2pPathData(String pathStr) {
        ThreadUtils.executeByCached(new ThreadUtils.SimpleTask<SweepPath>() {
            @Override
            public SweepPath doInBackground() {
                T4SweepPathParseUtil sweepMapParseUtil = new T4SweepPathParseUtil();
                SweepPath sweepPath = sweepMapParseUtil.parseSweepPath(pathStr);
                sweepPath.addPosArray(sweepPath.getPosArray());
                return sweepPath;
            }

            @Override
            public void onSuccess(SweepPath result) {
                if (result != null) {
                    LogUtils.w(result.getPathId() + "=====轨迹点个数---》" + result.getPointArrayList().size());
                    //设置地图数据
                    if (result.getPathId() != mCurPathId) {
                        ldMapView.getMapDataSet().cleanSweepPath();
                    }
                    ldMapView.getMapDataSet().setSweepPath(result.getPointArrayList());
                    mCurPathId = result.getPathId();
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (!isLoadMap) {
            ToastUtils.show(getString(R.string.no_map_tips));
            return;
        }
        switch (v.getId()) {
            case R.id.tvPartClean:   //暂无使用
                LDArea pointArea = ldMapView.getMapDataSet().getPointArea();
                ArrayList<LDArea> tmpAreaList = new ArrayList<>();
                tmpAreaList.add(pointArea);
                model.dealZoneArea(tmpAreaList, 1, new CommonListener<String>() {
                    @Override
                    public void onResult(String s) {
                        LogUtils.e(s);
                        Utils.sendDpCmd(mDevice, LdsConstant.DP_CODE.DP_COMMAND_TRANS, s, new IResultCallback() {
                            @Override
                            public void onError(String code, String error) {
                                LogUtils.e("下发划区指令失败");
                                ToastUtils.show(getString(R.string.network_error));
                            }

                            @Override
                            public void onSuccess() {
                                LogUtils.e("下发划区指令成功");
                            }
                        });
                    }
                });
                break;
            case R.id.tvGoHere:
                LDArea ldArea = ldMapView.getMapDataSet().getPointArea();
                PointF tapPoint = ldMapView.getMapDataSet().getTapPoint();
                if (tapPoint == null) {
                    ToastUtils.show(R.string.please_select_goal_point);
                    return;
                }
                displayLoading();
                model.dealGoToPoint(tapPoint, new CommonListener<String>() {
                    @Override
                    public void onResult(String s) {
                        LogUtils.e("定点信息--》" + s);
                        Utils.sendDpCmd(mDevice, LdsConstant.DP_CODE.DP_COMMAND_TRANS, s, new IResultCallback() {
                            @Override
                            public void onError(String code, String error) {
                                LogUtils.e("去这里指令失败");
                                ToastUtils.show(getString(R.string.network_error));
                                dismiss();
                            }

                            @Override
                            public void onSuccess() {
                                LogUtils.e("去这里指令成功");
                                countdownUtil.intervalTime(500)
                                        .totalTime(6000)
                                        .callback(new CountdownUtil.OnCountdownListener() {
                                            @Override
                                            public void onRemain(long millisUntilFinished) {

                                            }

                                            @Override
                                            public void onFinish() {
                                                countdownUtil.stop();
                                                ToastUtils.show(getString(R.string.network_error));
                                                dismiss();
                                            }
                                        }).start();
                            }
                        });
                    }
                });
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goBack();
        }
        return false;
    }

    /**
     * 退出页面，发送接续指令
     */
    private void goBack() {
//        EventBus.getDefault().post(new Event<Boolean>(Event.CODE_CMD_PAUSE_CLEAN, false));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //关闭动画
        ldMapView.getMapDataSet().cancelRobotBreath();
        if (mDevice != null) {
            mDevice.unRegisterDevListener();
            mDevice = null;
        }

        countdownUtil.stop();
    }
}
