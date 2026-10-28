package app.oncare.personal;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.ParcelUuid;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Foreground-only BLE transfer. No identifier or health value is advertised.
 * Challenge authentication and AES-GCM bind each immutable snapshot to a fresh connection.
 */
@SuppressLint("MissingPermission")
final class BleSyncServer {
    static final UUID SERVICE=UUID.fromString("3ef7d480-a27d-4db0-982c-183db690b101");
    static final UUID AUTH=UUID.fromString("3ef7d480-a27d-4db0-982c-183db690b102");
    static final UUID DATA=UUID.fromString("3ef7d480-a27d-4db0-982c-183db690b103");
    static final int LIMIT=1024*1024;
    private final Context context;
    private BluetoothGattServer gatt;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothDevice owner;
    private byte[] challenge,payload;
    private long expires;
    private int position,mtu=23;
    volatile String status="블루투스 꺼짐";
    BleSyncServer(Context context){this.context=context;}
    static byte[] mac(String token,byte[] challenge)throws Exception{
        Mac h=Mac.getInstance("HmacSHA256");h.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return h.doFinal(challenge);
    }
    static byte[] dataKey(String token,byte[] challenge)throws Exception{byte[] label="oncare-ble-data-v1".getBytes(StandardCharsets.UTF_8);return mac(token,ByteBuffer.allocate(challenge.length+label.length).put(challenge).put(label).array());}
    static byte[] seal(String token,byte[] challenge,byte[] plain)throws Exception{
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(dataKey(token,challenge),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(challenge);
        byte[] encrypted=cipher.doFinal(plain);return ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted).array();
    }
    private final AdvertiseCallback advertising=new AdvertiseCallback(){@Override public void onStartSuccess(AdvertiseSettings settings){status="블루투스 iPhone 연결 대기";}@Override public void onStartFailure(int error){stop();status="블루투스 광고 실패 · 다시 켜주세요";}};
    void start(){synchronized(callback){stop();try{
        BluetoothManager manager=context.getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()||!adapter.isMultipleAdvertisementSupported()){status="블루투스를 켜거나 지원 기기를 확인해주세요";return;}
        advertiser=adapter.getBluetoothLeAdvertiser();if(advertiser==null){status="블루투스 광고 미지원";return;}
        gatt=manager.openGattServer(context,callback);if(gatt==null)throw new IllegalStateException();
        BluetoothGattService service=new BluetoothGattService(UUID.fromString(LocalSyncServer.get(context).peer()),BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(new BluetoothGattCharacteristic(AUTH,BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(DATA,BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ));
        if(!gatt.addService(service))throw new IllegalStateException();status="블루투스 준비 중";
    }catch(Exception e){stop();status="근처 기기 권한·블루투스 상태를 확인해주세요";}}}
    void stop(){synchronized(callback){try{if(advertiser!=null)advertiser.stopAdvertising(advertising);if(gatt!=null)gatt.close();}catch(Exception ignored){}gatt=null;advertiser=null;owner=null;payload=null;challenge=null;status="블루투스 꺼짐";}}
    private boolean owns(BluetoothDevice device){return owner!=null&&owner.equals(device)&&System.currentTimeMillis()<expires;}
    private final BluetoothGattServerCallback callback=new BluetoothGattServerCallback(){
        @Override public synchronized void onServiceAdded(int result,BluetoothGattService service){if(gatt==null||advertiser==null)return;if(result!=BluetoothGatt.GATT_SUCCESS){stop();return;}try{advertiser.startAdvertising(new AdvertiseSettings.Builder().setConnectable(true).setTimeout(0).setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).build(),new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(service.getUuid())).build(),advertising);}catch(Exception e){stop();}}
        @Override public synchronized void onConnectionStateChange(BluetoothDevice device,int result,int state){if(gatt==null)return;if(state==BluetoothProfile.STATE_CONNECTED){if(owner!=null&&!owner.equals(device)){gatt.cancelConnection(device);return;}owner=device;challenge=new byte[32];new SecureRandom().nextBytes(challenge);payload=null;position=0;mtu=23;expires=System.currentTimeMillis()+150000;}else if(owner!=null&&owner.equals(device)){owner=null;payload=null;challenge=null;}}
        @Override public synchronized void onMtuChanged(BluetoothDevice device,int value){if(owns(device))mtu=Math.max(23,Math.min(517,value));}
        @Override public synchronized void onCharacteristicReadRequest(BluetoothDevice device,int request,int offset,BluetoothGattCharacteristic c){if(gatt==null)return;byte[] value=null;
            if(owns(device)&&AUTH.equals(c.getUuid())&&challenge!=null&&offset<=challenge.length)value=Arrays.copyOfRange(challenge,offset,challenge.length);
            if(owns(device)&&DATA.equals(c.getUuid())&&payload!=null&&offset==0){int count=Math.min(mtu-10,payload.length-position);value=ByteBuffer.allocate(8+count).putInt(position).putInt(payload.length).put(payload,position,count).array();}
            gatt.sendResponse(device,request,value==null?BluetoothGatt.GATT_FAILURE:BluetoothGatt.GATT_SUCCESS,offset,value);
        }
        @Override public synchronized void onCharacteristicWriteRequest(BluetoothDevice device,int request,BluetoothGattCharacteristic c,boolean prepared,boolean response,int offset,byte[] value){if(gatt==null)return;int result=BluetoothGatt.GATT_FAILURE;
            try{if(owns(device)&&AUTH.equals(c.getUuid())&&!prepared&&offset==0){
                if(value.length==17&&value[0]==1&&payload==null&&MessageDigest.isEqual(Arrays.copyOfRange(value,1,17),Arrays.copyOf(mac(LocalSyncServer.get(context).token(),challenge),16))){
                    // Read a completed cache; a missing or oversized snapshot never erases iPhone records.
                    byte[] plain=LocalSyncServer.get(context).bleSnapshot();if(plain==null||plain.length>LIMIT)throw new IllegalStateException();payload=seal(LocalSyncServer.get(context).token(),challenge,plain);position=0;result=BluetoothGatt.GATT_SUCCESS;status="암호화된 건강 기록 전송 중";
                }else if(value.length==5&&value[0]==2&&payload!=null){int next=ByteBuffer.wrap(value,1,4).getInt();if(next>=0&&next<payload.length){position=next;result=BluetoothGatt.GATT_SUCCESS;}}
            }}catch(Exception ignored){}
            if(response)gatt.sendResponse(device,request,result,0,null);
        }
    };
}
