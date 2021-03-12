package com.example.fw_updater_cy;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//主程序开始入口
public class MainActivity extends AppCompatActivity {
    private static final String USB_ACTION = "com.tcl.navigator.hostChart";
    private TextView info;
    private ProgressBar ProgressBar;
    private BufferedReader br;
    String s = "";
    private UsbManager myUsbManager;
    private UsbDevice myUsbDevice;
    private UsbInterface myInterface;
    private Context myContext;
    private UsbDeviceConnection myDeviceConnection;
    private UsbEndpoint myEndpointOut;
    private UsbEndpoint myEndpointIn;
    private UsbDeviceConnection myConnection = null;
    int fwsize;
    int WriteInIdx;
    String ProductName;
    byte[] fwbuffer = new byte[524288];
    private ExecutorService mThread;
    int CHKSUM;
    int TMP;
    int sendCount = 0;
    int over = 0;
    private Timer mTimer = null;
    private TimerTask mTimerTask = null;

    private Handler mHandler = new Handler() {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    info.append( "出错！未找到Bootloader...\n" );
                    break;
                case 11:
                    info.append( "程序重置到Bootloader模式...\n" );
                    break;
                case 12:
                    info.append( "跳转到Bootloader模式的命令发送成功\n" + "设备名称是：" + ProductName + "\n" );
                    break;
                case 13:
                    info.append( "已保持在Bootloader模式\n" + "设备名称是：" + ProductName + "\n" );
                    break;
                case 14:
                    info.append( "未保持在Bootloader模式\n" + "设备名称是：" + ProductName + "\n" );
                    break;
                case 15:
                    info.append( "跳转到Bootloader模式的命令发送失败\n" + "设备名称是：" + ProductName + "\n" );
                    break;
                case 16:
                    info.append( "it is BL\n" );
                    break;
                case 17:
                    Data_Erasure();
                    break;
                case 2:
                    info.append( "执行数据擦除...\n" );
                    break;
                case 21:
                    info.append( "数据发送失败1\n" );
                    break;
                case 22:
                    info.append( "flash数据擦除失败\n" );
                    break;
                case 23:
                    info.append( "flash数据擦除成功\n" );
                    break;
                case 24:
                    Write_Data();
                    break;
                case 3:
                    ProgressBar.setProgress( (100 * WriteInIdx) / fwsize );
                    break;
                case 31:
                    Mode_To_APP();
                    break;
                case 32:
                    info.append( "数据写入传输失败\n" );
                    break;
                case 4:
                    info.append( "数据写入传输完成，执行跳转回app模式...\n" );
                    break;
                case 41:
                    info.append( "跳转回app模式命令传输失败\n" );
                    break;
                case 42:
                    info.append( "完成本次烧录，设备名称是：" + ProductName + "\n\n" );
                    over = 1;
                    break;
                case 5:
                    info.append( "Checksum ok! Expected " + CHKSUM + "; received " + TMP + "sendCount:" + sendCount + "\n" );
                    break;
                case 51:
                    info.append( "Checksum error! Expected " + CHKSUM + "; received " + TMP + "sendCount:" + sendCount + "\n" );
                    break;
                case 52:
                    info.append( "ERROR: writing page failed.\n" );
                    break;
                case 53:
                    info.append( "bktbuf is error\n" );
                    break;
                case 6:
                    info.append( "获取设备超时，获取设备失败\n" );
                    break;
                case 61:
                    info.append( "myUsbManager is null\n" );
                    break;
                case 62:
                    info.append( "打开设备成功\n" );
                    break;
                case 7:
                    int offSet = info.getLineCount() * info.getLineHeight();
                    if (offSet > info.getHeight()) {
                        info.scrollTo( 0, offSet - info.getHeight() );
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );
        info = (TextView) findViewById( R.id.info );
        info.setMovementMethod( ScrollingMovementMethod.getInstance() );
        ProgressBar = (ProgressBar) findViewById( R.id.progressBar );
        myUsbManager = (UsbManager) getSystemService( USB_SERVICE );
        mThread = Executors.newFixedThreadPool( 10 );
        mTimer = new Timer();
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage( 7 );
            }
        };
//        mTimer.schedule( mTimerTask, 0, 20 );
    }

    public void Close(View view) {
        finish();
    }

    public void UpData(View view) {
        if (Build.VERSION.SDK_INT >= 23) {
            int REQUEST_CODE_CONTACT = 101;
            String[] permissions = {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
            //验证是否许可权限
            for (String str : permissions) {
                if (this.checkSelfPermission( str ) != PackageManager.PERMISSION_GRANTED) {
                    //申请权限
                    this.requestPermissions( permissions, REQUEST_CODE_CONTACT );
                    return;
                } else {
                    //这里就是权限打开之后自己要操作的逻辑
                    Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
                    intent.setType( "*/*" );//设置类型，我这里是任意类型，任意后缀的可以这样写。
                    intent.addCategory( Intent.CATEGORY_OPENABLE );
                    startActivityForResult( intent, 1 );
                }
            }
        } else {
            //这里就是权限打开之后自己要操作的逻辑
            Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
            intent.setType( "*/*" );//设置类型，我这里是任意类型，任意后缀的可以这样写。
            intent.addCategory( Intent.CATEGORY_OPENABLE );
            startActivityForResult( intent, 1 );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult( requestCode, resultCode, data );
        if (resultCode == Activity.RESULT_OK) {//是否选择，没选择就不会继续
            Uri uri = data.getData();//得到uri，后面就是将uri转化成file的过程。
            String FilePath = uri.getPath();
            try {
                getFileName( FilePath );
            } catch (IOException e) {
                e.printStackTrace();
            }
            Toast.makeText( MainActivity.this, FilePath, Toast.LENGTH_SHORT ).show();
        }
    }

    private void getFileName(String filePath) throws IOException {
        // TODO Auto-generated method stub
        int index = filePath.indexOf( "Download" );
        String path = filePath.substring( index );
        File FilePath = new File( "/sdcard/" + path );
        info.setText( "上传的文件：" + FilePath.toString() + "\n" );
        InputStream Stream = new FileInputStream( FilePath );
        fwsize = Stream.available();
        byte[] buffer = new byte[fwsize];
        Stream.read( buffer );
        fwbuffer = buffer;
        info.append( "文件大小：" + fwsize + "\n" );
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void Run(View view) {
        if (connect() == 0) {
            return;
        }
        if (fwsize < 1) {
            info.append( "出错！未上传文件，请上传文件...\n" );
            return;
        } else {
            info.append( "设备名称是:" + ProductName + "\n" );
        }
        Judgment_Mode();
//        Data_Erasure();
//        Write_Data();
//        Mode_To_APP();

        return;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void Judgment_Mode() {
        final long[] i = {300};
        mThread.execute( new Runnable() {
            @Override
            public void run() {
                if (myUsbDevice != null) {
                    while (!ProductName.equals( "BL" )) {
                        if (i[0] > 1000) {
                            mHandler.sendEmptyMessage( 1 );//出错！未找到Bootloader
                            break;
                        }
                        mHandler.sendEmptyMessage( 11 );//程序重置到Bootloader模式

                        byte[] buf = {0x01, (byte) 0x83, (byte) 0x81, (byte) 0x83, 0x00};
                        int res = myConnection.bulkTransfer( myEndpointOut, buf, buf.length, 1000 );
                        try {
                            Thread.sleep( 500 );
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        myConnection.close();
                        if (res > 0 && connect() == 1) {
                            mHandler.sendEmptyMessage( 12 );//跳转到Bootloader模式的命令发送成功,设备名称是
                            res = myConnection.bulkTransfer( myEndpointOut, buf, buf.length, 1000 );
                            try {
                                Thread.sleep( 1000 );
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            myConnection.close();
                            if (res > 0 && connect() == 1) {
//                                connect();
                                mHandler.sendEmptyMessage( 13 );//已保持在Bootloader模式,设备名称是

                            } else {
//                                connect();
                                mHandler.sendEmptyMessage( 14 );//未保持在Bootloader模式,设备名称是
                            }
                        } else {
//                            connect();
                            mHandler.sendEmptyMessage( 15 );//跳转到Bootloader模式的命令发送失败,设备名称是
                        }
                        i[0] += 100;
                    }
                    mHandler.sendEmptyMessage( 16 );//it is BL
                }
                mHandler.sendEmptyMessage( 17 );
            }
        } );
    }

    private void Data_Erasure() {
        char sz = 0x00;
        final int[] flag = {0};
        if (fwsize > (128 * 1024)) {
            sz = 0x05;
        } else if (fwsize > (64 * 1024)) {
            sz = 0x04;
        } else if (fwsize > (32 * 1024)) {
            sz = 0x03;
        } else if (fwsize > (16 * 1024)) {
            sz = 0x02;
        } else if (fwsize > (8 * 1024)) {
            sz = 0x01;
        }
        final char finalSz = sz;
        mThread.execute( new Runnable() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage( 2 );//执行数据擦除
                byte[] buf = {0x01, (byte) 0x82, (byte) 0x8A, (byte) finalSz};
                int res = myConnection.bulkTransfer( myEndpointOut, buf, buf.length, 1000 );
                if (res < 0) {
                    mHandler.sendEmptyMessage( 21 );//数据发送失败1
                    return;
                }
                try {
                    Thread.sleep( 1000 );
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                byte[] InBuf = new byte[64];
                res = -1;
                while (res < 0) {
                    res = myConnection.bulkTransfer( myEndpointIn, InBuf, InBuf.length, 1000 );
                }
                try {
                    Thread.sleep( 1000 );
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if ((InBuf[1] != (byte) 0x81) || (InBuf[2] != (byte) 0x80)) {
                    mHandler.sendEmptyMessage( 22 );//flash数据擦除失败
                    return;
                } else {
                    mHandler.sendEmptyMessage( 23 );//flash数据擦除成功
                    flag[0] = 1;
                }

                byte[] LastBuf = new byte[64];
//                res = -1;
//                while (res < 0) {
                res = myConnection.bulkTransfer( myEndpointIn, LastBuf, LastBuf.length, 1000 );
//                }
                if (flag[0] == 1) {
                    mHandler.sendEmptyMessage( 24 );
                    flag[0] = 0;
                }
            }
        } );
    }


    private void Write_Data() {
        info.append( "执行数据写入传输.....\n" );
        final int[] previdx = new int[1];
        WriteInIdx = 0;
        final int[] pageid = {0};
        final int[] res = new int[1];
        mThread.execute( new Runnable() {
            @Override
            public void run() {
                while (true) {
                    previdx[0] = WriteInIdx;
                    res[0] = 0;
                    while (res[0] < 1) {
                        res[0] = send_page( previdx[0] );
                        if (res[0] < 0) {
                            mHandler.sendEmptyMessage( 32 );//数据写入传输失败
                            break;
                        }
                        try {
                            Thread.sleep( 10 );
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    WriteInIdx = res[0];
                    write_page( pageid[0] );
                    mHandler.sendEmptyMessage( 3 );
                    pageid[0]++;
                    if (WriteInIdx >= fwsize) {
                        break;
                    }
                }
                mHandler.sendEmptyMessage( 31 );
            }
        } );
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void Mode_To_APP() {
        mThread.execute( new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep( 1000 );
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mHandler.sendEmptyMessage( 4 );//数据写入传输完成，执行跳转回app模式
                byte[] ToAppBuf = new byte[]{0x01, (byte) 0x83, (byte) 0x81, (byte) 0x80};
                int res = myConnection.bulkTransfer( myEndpointOut, ToAppBuf, ToAppBuf.length, 1000 );
                if (res < 0) {
                    mHandler.sendEmptyMessage( 41 );//跳转会app模式命令传输失败
                }
                myConnection.close();
                try {
                    Thread.sleep( 1000 );
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                connect();
                try {
                    Thread.sleep( 1000 );
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mHandler.sendEmptyMessage( 42 );//完成本次烧录，设备名称是
            }
        } );
    }

    private void write_page(int pageid) {
        byte[] buf = new byte[5];
        byte[] buff = new byte[64];

        buf[0] = 0x01;
        buf[1] = (byte) 0x83;
        buf[2] = (byte) 0x8C;
        buf[3] = 0x00;
        buf[4] = 0x00;

        if (pageid > 0xFF) {
            buf[3] = (byte) (pageid - 256);
            buf[4] = (byte) (pageid / 256);
        } else {
            buf[3] = (byte) pageid;
        }

        myConnection.bulkTransfer( myEndpointOut, buf, buf.length, 1000 );
        myConnection.bulkTransfer( myEndpointIn, buff, buff.length, 1000 );

        if (buff[2] == (byte) 0x81) {
            mThread.execute( new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage( 52 );//ERROR: writing page failed.
                }
            } );
            return;
        }
        return;
    }

    private int send_page(int idx) {
        int chks = 0;
        byte[] buf = new byte[35];
        for (int i = 0; i < 4; i++) {
            buf[0] = 0x01;
            buf[1] = (byte) 0xA1;
            buf[2] = (byte) 0x8B;
            for (int j = 0; j < 32; j++) {
                if (idx < fwsize) {
                    buf[j + 3] = fwbuffer[idx];
                    idx++;
                } else {
                    buf[j + 3] = 0;
                }
            }
            chks = chks + sendpkt( buf );
        }
        if (chks > 0) {
            return 0;
        } else if (chks < 0) {
            return -1;
        } else {
            return idx;
        }
    }

    private int sendpkt(byte[] buf) {
        byte[] buff = new byte[64];
        int chksum = 0;
        int res;
        int tmp;
        for (int i = 0; i < 16; i++) {
            tmp = ((buf[3 + (i * 2)] & 0x0FF) << 8) + (buf[3 + (i * 2 + 1)] & 0x0FF);
            chksum += tmp;
        }
        chksum &= 0xffff;

        res = -1;
        while (res < 0) {
            res = myConnection.bulkTransfer( myEndpointOut, buf, buf.length, 1000 );
        }
        if (res < 1) {
            mThread.execute( new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage( 53 );//bktbuf is error
                }
            } );
            return 1;
        }
        res = -1;
        while (res < 0) {
            res = myConnection.bulkTransfer( myEndpointIn, buff, buff.length, 1000 );
        }

        CHKSUM = chksum;
        tmp = ((buff[4] & 0x0FF) << 8) + (buff[3] & 0x0FF);
        TMP = tmp;
        if ((buff[2] == (byte) 0x80) && (tmp == chksum)) {
//            mThread.execute( new Runnable() {
//                @Override
//                public void run() {
//                    sendCount++;
//                    mHandler.sendEmptyMessage( 5 );//Checksum ok! Expected " + chksum + "; received " + tmp + "sendCount:" + sendCount
//                }
//            } );
            return 0;
        } else {
            mThread.execute( new Runnable() {
                @Override
                public void run() {
                    sendCount++;
                    mHandler.sendEmptyMessage( 51 );//Checksum error! Expected " + chksum + "; received " + tmp + "sendCount:" + sendCount
                }
            } );
            return 1;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public int connect() {
        int count = 0;
        while (myDeviceConnection == null) {
            enumerateDevice(); //枚举设备
            findInterface();   //找设备接口
            openDevice();      //打开设备
            assignEndpoint();  //分配端点
            count++;
            if (count >= 1000) {
                break;
            }
        }
        if (count == 1000) {
            mThread.execute( new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage( 6 );//获取设备超时，获取设备失败
                }
            } );
            return 0;
        }
        myDeviceConnection = null;
        return 1;
    }

    /**
     * 分配端点，IN | OUT，即输入输出；此处我直接用1为OUT端点，0为IN，当然你也可以通过判断
     */
    //USB_ENDPOINT_XFER_BULK
     /*
     #define USB_ENDPOINT_XFER_CONTROL 0 --控制传输
     #define USB_ENDPOINT_XFER_ISOC 1 --等时传输
     #define USB_ENDPOINT_XFER_BULK 2 --块传输
     #define USB_ENDPOINT_XFER_INT 3 --中断传输
     * */
    private void assignEndpoint() {
        if (myInterface == null) {
            return;
        }

        for (int i = 0; i < myInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = myInterface.getEndpoint( i );
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    myEndpointOut = endpoint;
                } else {
                    myEndpointIn = endpoint;
                }
            }
        }
//        info.append( "myEndpointOut:" + myEndpointOut + "\n" + "myEndpointIn:" + myEndpointIn + "\n");
    }

    /**
     * 打开设备
     */
    private void openDevice() {
        if (myInterface == null) {
            return;
        }

        // 在open前判断是否有连接权限；对于连接权限可以静态分配，也可以动态分配权限，可以查阅相关资料
        if (myUsbManager.hasPermission( myUsbDevice )) {
//            info.append( "USB使用权限通过\n" );
            myConnection = myUsbManager.openDevice( myUsbDevice );
        } else {
//            info.append( "USB使用权限配置\n" );
            myContext = getApplicationContext();
            PendingIntent pendingIntent = PendingIntent.getBroadcast( myContext, 0,
                    new Intent( USB_ACTION ), 0 );
            myUsbManager.requestPermission( myUsbDevice, pendingIntent );
            myConnection = myUsbManager.openDevice( myUsbDevice );
        }
        if (myConnection == null) {
//            info.append( "UsbDeviceConnection失败\n" );
            return;
        }
        if (myConnection.claimInterface( myInterface, true )) {
            myDeviceConnection = myConnection;
            mThread.execute( new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage( 62 );//打开设备成功
                }
            } );
        } else {
            myConnection.close();
        }
    }

    /**
     * 找设备接口
     */
    private void findInterface() {
        if (myUsbDevice == null) {
            return;
        }
        myInterface = myUsbDevice.getInterface( 0 );
    }

    /**
     * 枚举设备
     */
    @SuppressLint("NewApi")
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void enumerateDevice() {
        if (myUsbManager == null) {
            mThread.execute( new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage( 61 );//myUsbManager is null
                }
            } );
            return;
        }

//        info.append( "this is enumerateDevice\n" );
        HashMap<String, UsbDevice> deviceList = myUsbManager.getDeviceList();
//        info.append( deviceList.toString() );
        if (!deviceList.isEmpty()) {
//            info.append( "deviceList is error\n" );
            StringBuffer sb = new StringBuffer();
            for (UsbDevice device : deviceList.values()) {
                if (device.getVendorId() == 1046) {
                    sb.append( device.toString() );
                    sb.append( "\n" );
//                info.setText( sb );
//                info.append( "DeviceInfo:VendorId-" + device.getVendorId() + " ProductId-" + device.getProductId() + "\n" );
                    ProductName = device.getProductName();
                    myUsbDevice = device;
                }
            }
        }
    }
}

