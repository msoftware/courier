package me.denley.courier;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used to start and stop receiving callbacks on annotated fields and methods.
 * This is done with the {@link #startReceiving} and {@link #stopReceiving} methods.
 *
 * This class also contains convenience methods for sending messages and data through the wearable API.
 */
@SuppressWarnings("unused")
public final class Courier {

    /** For use by generated code. Don't use this. */
    public static final String CLASS_SUFFIX = "$$Delivery";

    private static final Map<Class, DeliveryBoy> DELIVERY_STAFF = new LinkedHashMap<>();

    /** For use by generated code. Don't use this. */
    public interface DeliveryBoy<T> {
        public void startReceiving(GoogleApiClient apiClient, T target);
        public void stopReceiving(T target);
    }

    private interface WearableApiTask {
        public void run(GoogleApiClient apiClient);
    }

    /**
     * Puts the given object to the specified path in the Wearable.DataApi.
     *
     * This can be called safely from any thread (it will occur asynchronously).
     *
     * @param context   The Context used to connect to the wearable API.
     * @param path      The path on which to place the data.
     * @param data      The object to serialize and send to the wearable API on the given path.
     */
    public static void deliverData(final Context context, final String path, final Object data) {
        makeWearableApiCall(context, new WearableApiTask() {
            @Override public void run(GoogleApiClient apiClient) {
                final PutDataRequest request = Packager.pack(path, data);
                Wearable.DataApi.putDataItem(apiClient, request);
            }
        });
    }

    /**
     * Sends the given object as a message to all other connected devices
     *
     * This can be called safely from any thread (it will occur asynchronously).
     *
     * @param context   The Context used to connect to the wearable API.
     * @param path      The path on which to send the message.
     * @param data      The object to serialize and send.
     */
    public static void deliverMessage(final Context context, final String path, final Object data) {
        makeWearableApiCall(context, new WearableApiTask() {
            @Override public void run(GoogleApiClient apiClient) {
                final byte[] bytes = Packager.packBytes(data);

                final List<Node> nodes = Wearable.NodeApi.getConnectedNodes(apiClient).await().getNodes();
                for (Node node : nodes) {
                    Wearable.MessageApi.sendMessage(apiClient, node.getId(), path, bytes);
                }
            }
        });
    }

    /**
     * Sends the given object as a message to a single connected device. If the destination device is
     * not connected, this message will not be sent.
     *
     * This can be called safely from any thread (it will occur asynchronously).
     *
     * @param context   The Context used to connect to the wearable API.
     * @param path      The path on which to send the message.
     * @param destinationNodeId The ID of the destination node.
     * @param data      The object to serialize and send.
     */
    public static void deliverMessage(final Context context, final String path, final String destinationNodeId, final Object data) {
        makeWearableApiCall(context, new WearableApiTask() {
            @Override public void run(GoogleApiClient apiClient) {
                final byte[] bytes = Packager.packBytes(data);
                Wearable.MessageApi.sendMessage(apiClient, destinationNodeId, path, bytes);
            }
        });
    }

    /**
     * Deletes all data items on the given path.
     *
     * This can be called safely from any thread (it will occur asynchronously).
     *
     * @param context The Context used to connect to the wearable API.
     * @param path The path on which to delete every data item.
     */
    public static void deleteData(final Context context, final String path) {
        deleteData(context, path, null);
    }

    /**
     * Deletes a single data item on the given path from the given node. If the target node
     * is disconnected, this will occur next time the node is connected to this device.
     *
     * This can be called safely from any thread (it will occur asynchronously).
     *
     * @param context The Context used to connect to the wearable API.
     * @param path The path on which to delete the data item
     * @param nodeId The node that created the data item to be removed.
     */
    public static void deleteData(final Context context, final String path, final String nodeId) {
        makeWearableApiCall(context, new WearableApiTask(){
            @Override public void run(GoogleApiClient apiClient) {
                final Uri.Builder uri = new Uri.Builder();
                uri.scheme("wear");
                uri.encodedPath(path);
                if(nodeId!=null) {
                    uri.encodedAuthority(nodeId);
                }

                Wearable.DataApi.deleteDataItems(apiClient, uri.build());
            }
        });
    }

    /**
     * Retrieves and returns a Node representing this device. This must not be called
     * on the main thread.
     *
     * @param context The Context used to connect to the wearable API.
     * @return a Node representing this device.
     */
    public static Node getLocalNode(final Context context) {
        if(Looper.myLooper()==Looper.getMainLooper()) {
            throw new IllegalStateException("getLocalNode can not be called from the UI thread");
        }

        final GoogleApiClient apiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();

        final ConnectionResult result = apiClient.blockingConnect();

        if(result.isSuccess()) {
            return Wearable.NodeApi.getLocalNode(apiClient).await().getNode();
        } else {
            return null;
        }
    }

    /**
     * Retrieves and returns an InputStream for reading the data from an Asset. This must not be called
     * on the main thread.
     *
     * @param context The Context used to connect to the wearable API.
     * @param asset The asset to open a stream for.
     * @return An InputStream containing the data for the given asset.
     */
    public static InputStream getAssetInputStream(final Context context, final Asset asset) {
        if(Looper.myLooper()==Looper.getMainLooper()) {
            throw new IllegalStateException("getAssetInputStream can not be called from the UI thread");
        }

        final GoogleApiClient apiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();

        final ConnectionResult result = apiClient.blockingConnect();
        if(result.isSuccess()) {
            return Wearable.DataApi.getFdForAsset(apiClient, asset).await().getInputStream();
        } else {
            return null;
        }
    }

    /**
     * Starts receiving message, data, an device connection events on a target object.
     * Be sure to call {@link #stopReceiving} when you no longer want to receive updates.
     *
     * @param context The Context used to connect to the wearable API.
     * @param target The object to start receiving callbacks/bindings.
     */
    public static <T> void startReceiving(final Context context, final T target) {
        final DeliveryBoy<T> messenger = findDeliveryBoy(target.getClass());

        makeWearableApiCall(context, new WearableApiTask() {
            @Override public void run(GoogleApiClient apiClient) {
                messenger.startReceiving(apiClient, target);
            }
        });
    }

    /**
     * Starts receiving message, data, an device connection events on a target object.
     * Be sure to call {@link #stopReceiving} when you no longer want to receive updates.
     *
     * @param target The object to start receiving callbacks/bindings.
     */
    public static void startReceiving(final Activity target) {
        startReceiving(target, target);
    }

    /**
     * Starts receiving message, data, an device connection events on a target object.
     * Be sure to call {@link #stopReceiving} when you no longer want to receive updates.
     *
     * @param target The object to start receiving callbacks/bindings.
     */
    public static void startReceiving(final Fragment target) {
        startReceiving(target.getActivity(), target);
    }

    /**
     * Starts receiving message, data, an device connection events on a target object.
     * Be sure to call {@link #stopReceiving} when you no longer want to receive updates.
     *
     * @param target The object to start receiving callbacks/bindings.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void startReceiving(final android.app.Fragment target) {
        startReceiving(target.getActivity(), target);
    }

    /**
     * Starts receiving message, data, an device connection events on a target object.
     * Be sure to call {@link #stopReceiving} when you no longer want to receive updates.
     *
     * @param target The object to start receiving callbacks/bindings.
     */
    public static void startReceiving(final View target) {
        startReceiving(target.getContext(), target);
    }

    /**
     * Starts receiving message, data, an device connection events on a target object.
     * Be sure to call {@link #stopReceiving} when you no longer want to receive updates.
     *
     * @param target The object to start receiving callbacks/bindings.
     */
    public static void startReceiving(final Dialog target) {
        startReceiving(target.getContext(), target);
    }

    /**
     * Starts receiving message, data, an device connection events on a target object.
     * Be sure to call {@link #stopReceiving} when you no longer want to receive updates.
     *
     * @param target The object to start receiving callbacks/bindings.
     */
    public static void startReceiving(final Service target) {
        startReceiving(target, target);
    }

    /**
     * Stops receiving message, data, an device connection events on a target object.
     * Since callbacks are made asynchronously, it is possible that callbacks may occur
     * for a short time after this method is called.
     *
     * @param target The object to start receiving callbacks/bindings.
     */
    public static <T> void stopReceiving(final T target) {
        final DeliveryBoy<T> messenger = findDeliveryBoy(target.getClass());
        messenger.stopReceiving(target);
    }

    private static void makeWearableApiCall(final Context context, final WearableApiTask task) {
        new Thread(){
            public void run() {
                final GoogleApiClient apiClient = new GoogleApiClient.Builder(context)
                        .addApi(Wearable.API)
                        .build();

                final ConnectionResult result = apiClient.blockingConnect();

                if(result.isSuccess()) {
                    task.run(apiClient);
                }
            }
        }.start();
    }

    @SuppressWarnings("unchecked")
    private static <T> DeliveryBoy<T> findDeliveryBoy(Class targetClass) {
        DeliveryBoy<T> messenger = DELIVERY_STAFF.get(targetClass);
        if(messenger!=null) {
            return messenger;
        }

        try {
            final String messengerClassName = targetClass.getName() + CLASS_SUFFIX;
            final Class messengerClass = Class.forName(messengerClassName);
            messenger = (DeliveryBoy<T>)messengerClass.newInstance();
        }catch (Exception e) {
            Class superClass = targetClass.getSuperclass();
            if(superClass==Object.class) {
                throw new IllegalStateException("Courier not found for "+targetClass.getName()+". Missing annotations?");
            } else {
                messenger = findDeliveryBoy(superClass);
            }
        }

        DELIVERY_STAFF.put(targetClass, messenger);
        return messenger;
    }


    // Don't allow instantiation
    private Courier(){}

}
