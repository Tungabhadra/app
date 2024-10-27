/*
 * Copyright (c) 2016-2018, 2023 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.snpe.imageclassifiers;

import static android.app.Activity.RESULT_OK;
import static android.content.ContentValues.TAG;
import static android.content.Context.CAMERA_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qualcomm.qti.snpe.NeuralNetwork;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.qualcomm.qti.snpe.imageclassifiers.ModelOverviewFragmentController.SupportedTensorFormat;

public class ModelOverviewFragment extends Fragment {

    private static final int PICK_IMAGE = 1;
    public static final String EXTRA_MODEL = "model";

    private static final String[] cpuModes = {"FLOAT_32", "FXP_8"};

    private static final String[] cpuMode_empty = {"N/A"};

    private static final Locale LOCALE = Locale.CANADA;

    private ModelImagesAdapter mImageGridAdapter;

    private ModelOverviewFragmentController mController;

    private TextView mDimensionsText;

    private TextView mModelNameText;

    private Spinner mOutputLayersSpinners;

    private Spinner mRuntimeSpinner;

    private Spinner mTensorFormatSpinner;

    private Spinner mCpuModeSpinner;

    private TextView mClassificationText;

    private TextView mModelVersionText;

    private TextView mStatisticLoadText;

    private TextView mStatisticJavaExecuteText;

    private Button mBuildButton;
    private Button mSelectButton;

    static CameraDevice cameraDevice;
    CaptureRequest.Builder captureRequestBuilder;
    static CameraCaptureSession cameraCaptureSession;

    Context context;
    CameraManager manager;

    String lid;
    SurfaceView surfaceView;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;


    private static boolean mUnsignedPD;

    public static ModelOverviewFragment create(final Model model, boolean unsignedPD) {
        mUnsignedPD = unsignedPD;
        final ModelOverviewFragment fragment = new ModelOverviewFragment();
        final Bundle arguments = new Bundle();
        arguments.putParcelable(EXTRA_MODEL, model);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_model, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mModelNameText = (TextView) view.findViewById(R.id.model_overview_name_text);
        mModelVersionText = (TextView) view.findViewById(R.id.model_overview_version_text);
        mDimensionsText = (TextView) view.findViewById(R.id.model_overview_dimensions_text);
        mRuntimeSpinner = (Spinner) view.findViewById(R.id.model_builder_runtime_spinner);
        mTensorFormatSpinner = (Spinner) view.findViewById(R.id.model_builder_tensor_spinner);
        mCpuModeSpinner = (Spinner) view.findViewById(R.id.model_builder_cpu_mode_spinner);
        mOutputLayersSpinners = (Spinner) view.findViewById(R.id.model_overview_layers_spinner);
        mClassificationText = (TextView) view.findViewById(R.id.model_overview_classification_text);
        mStatisticLoadText = (TextView) view.findViewById(R.id.model_statistics_init_text);
        mStatisticJavaExecuteText = (TextView) view.findViewById(R.id.model_statistics_java_execute_text);

        surfaceView = view.findViewById(R.id.surfaceView);

        mSelectButton = view.findViewById(R.id.select_button);
        mBuildButton = (Button) view.findViewById(R.id.model_build_button);
        mBuildButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mController.loadNetwork();
            }
        });

        mSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGallery();
            }
        });

         startBackgroundThread();

         openCamera();
    }

    private void openGallery() {
//        System.out.println(mController.);
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            try {
                Bitmap bitmap = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContext().getContentResolver(), selectedImageUri);
                }

                System.out.println(bitmap.getWidth() + " lolo " + bitmap.getHeight());
                mController.classify(bitmap);
                // Now you can use the bitmap (e.g., display it in an ImageView)
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        final Model model = getArguments().getParcelable(EXTRA_MODEL);
        mController = new ModelOverviewFragmentController(
                (Application) getActivity().getApplicationContext(), model, mUnsignedPD);
    }

    @Override
    public void onStart() {
        super.onStart();
        mController.attach(this);
    }

    @Override
    public void onStop() {
        mController.detach(this);
        super.onStop();
    }

    public void addSampleBitmap(Bitmap bitmap) {
        if (mImageGridAdapter.getPosition(bitmap) == -1) {
            mImageGridAdapter.add(bitmap);
            mImageGridAdapter.notifyDataSetChanged();
        }
    }

    public void setNetworkDimensions(int[] inputDimensions) {
        mDimensionsText.setText(inputDimensions != null ? Arrays.toString(inputDimensions) : "");
    }

    public void displayModelLoadFailed() {
        Toast.makeText(getActivity(), R.string.model_load_failed, Toast.LENGTH_SHORT).show();
    }

    public void setModelName(String modelName) {
        mModelNameText.setText(modelName);
    }

    public void setModelVersion(String version) {
        mModelVersionText.setText(version);
    }

    public void setModelLoadTime(long loadTime) {
        if (loadTime > 0) {
            mStatisticLoadText.setText(String.format(LOCALE, "%d ms", loadTime));
        } else {
            mStatisticLoadText.setText(R.string.not_available);
        }
    }

    public void setJavaExecuteStatistics(long javaExecuteTime) {
        if (javaExecuteTime > 0) {
            mStatisticJavaExecuteText.setText(String.format(LOCALE, "%d ms", javaExecuteTime));
        } else {
            mStatisticJavaExecuteText.setText(R.string.not_available);
        }
    }

    public void setOutputLayersNames(Set<String> outputLayersNames) {
        mOutputLayersSpinners.setAdapter(new ArrayAdapter<>(
            getActivity(), android.R.layout.simple_list_item_1,
            new LinkedList<>(outputLayersNames)));
    }

    public void setSupportedTensorFormats(List<SupportedTensorFormat> tensorsFormats) {
        mTensorFormatSpinner.setAdapter(new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_list_item_1, tensorsFormats
        ));

        mTensorFormatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                SupportedTensorFormat format = (SupportedTensorFormat) adapterView.getItemAtPosition(i);
                mController.setTensorFormat(format);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mController.setTensorFormat(SupportedTensorFormat.FLOAT);
            }
        });
    }

    public void setSupportedRuntimes(List<NeuralNetwork.Runtime> runtimes) {
        mRuntimeSpinner.setAdapter(new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_list_item_1, runtimes
        ));

        mRuntimeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if((NeuralNetwork.Runtime)mRuntimeSpinner.getSelectedItem() == NeuralNetwork.Runtime.CPU) {
                    mCpuModeSpinner.setAdapter(new ArrayAdapter<String>(
                            getActivity(), android.R.layout.simple_list_item_1, cpuModes
                    ));
                }
                else{
                    mCpuModeSpinner.setAdapter(new ArrayAdapter<String>(
                            getActivity(), android.R.layout.simple_list_item_1, cpuMode_empty
                    ));
                }

                mCpuModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        String mode = (String) adapterView.getItemAtPosition(i);
                        mController.setCpuMode(mode);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        if((NeuralNetwork.Runtime)mRuntimeSpinner.getSelectedItem() == NeuralNetwork.Runtime.CPU)
                            mController.setCpuMode(cpuModes[0]);
                    }
                });

                NeuralNetwork.Runtime runtime = (NeuralNetwork.Runtime) parentView.getItemAtPosition(position);
                mController.setTargetRuntime(runtime);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                mController.setTargetRuntime(NeuralNetwork.Runtime.CPU);
            }
        });
    }

    public void setClassificationResult(String[] classificationResult) {
        if (classificationResult.length > 0) {
            mClassificationText.setText(
                    String.format("%s: %s", classificationResult[0], classificationResult[1]));
        } else {
            setClassificationHint();
        }
    }

    public void setClassificationHint() {
        mClassificationText.setText(R.string.classification_hint);
    }

    public void setLoadingNetwork(boolean loading) {
        if (loading) {
            mBuildButton.setText(R.string.loading_network);
            mBuildButton.setEnabled(false);
        } else {
            mBuildButton.setText(R.string.build_network);
            mBuildButton.setEnabled(true);
        }
    }

    public void displayModelNotLoaded() {
        Toast.makeText(getActivity(), R.string.model_not_loaded, Toast.LENGTH_SHORT).show();
    }

    public void displayClassificationFailed() {
        setClassificationHint();
        Toast.makeText(getActivity(), R.string.classification_failed, Toast.LENGTH_SHORT).show();
    }

    private static class ModelImagesAdapter extends ArrayAdapter<Bitmap> {

        ModelImagesAdapter(Context context) {
            super(context, R.layout.model_image_layout);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = LayoutInflater.from(parent.getContext()).
                    inflate(R.layout.model_image_layout, parent, false);
            } else {
                view = convertView;
            }

            final ImageView imageView = ImageView.class.cast(view);
            imageView.setImageBitmap(getItem(position));
            return view;
        }
    }

    void openCamera()
    {
        CameraManager manager = (CameraManager) getContext().getSystemService(CAMERA_SERVICE);

        try {

            startBackgroundThread();

            lid = "0";

            CameraCharacteristics camera = manager.getCameraCharacteristics(lid);
            Log.d(TAG, "Camera " + lid + " capabilities: " + Arrays.toString(camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)));
            Log.d(TAG, "Is logical multi-camera: " + camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES));

            for (int i : camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)) {
                if (i == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
                    System.out.println("Logical camera confirmed");
                }
            }


            CameraCharacteristics chars = manager.getCameraCharacteristics(lid);
            int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            for (int capability : capabilities) {
                Log.d(TAG, "Camera capability: " + capability);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                System.out.println(manager.getConcurrentCameraIds());
            }

//        (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));

            Size previewSize = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.PRIVATE)[9]; // Get a suitable preview size


            float deviation = Float.MAX_VALUE;
            float ideal = (float) surfaceView.getWidth() / surfaceView.getHeight();
            Size best = new Size(500, 500);

            String dimension = "";

            for (Size size : camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.PRIVATE)) {
                float ratio = (float) size.getWidth() / size.getHeight();

                if (Math.abs(ratio - ideal) < deviation) {
                    deviation = Math.abs(ratio - ideal);
                    best = size;
                }

                System.out.println("width " + size.getWidth() + " height " + size.getHeight());
                dimension += "width " + size.getWidth() + " height " + size.getHeight() + "\n";
            }

            int x = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.PRIVATE).length;
            best = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.PRIVATE)[3];
//            size = best;
            surfaceView.getHolder().setFixedSize(best.getWidth(), best.getHeight());
//            s1.getHolder().setFixedSize(best.getWidth(), best.getHeight());

            dimension += "chose " + best.getWidth() + " " + best.getHeight() + "\n";

            // System.out.println(camera.get(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));

            // showDialog(dimension);
            // Create an ImageReader to handle the preview frames
            // ImageReader imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getWidth(), ImageFormat.PRIVATE, 1);
            // reprocessSurface = imageReader.getSurface();

            if (this.getActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            Log.d(TAG, "Trying to open camera" + lid);
            manager.openCamera(lid, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Opened " + lid);

                    // showDialog("Opened id " + lid);

                    cameraDevice = camera;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    if (error == ERROR_MAX_CAMERAS_IN_USE) {
                        Log.d(TAG, "cant open camera" + lid);
                    }
                    camera.close();
                    cameraDevice = null;
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    public void createCameraPreviewSession() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

//            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            captureRequestBuilder.addTarget(s1.getHolder().getSurface());
//            captureRequestBuilder.addTarget(s2.getHolder().getSurface());

            ImageReader mImageReader = ImageReader.newInstance(surfaceView.getWidth(), surfaceView.getHeight(), ImageFormat.YUV_420_888, 1);
            Surface mImageSurface = mImageReader.getSurface();

            mImageReader.setOnImageAvailableListener(imageAvailableListener, mBackgroundHandler);

            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            captureRequestBuilder.addTarget(surfaceView.getHolder().getSurface());
            captureRequestBuilder.addTarget(mImageSurface);

            OutputConfiguration config1 = new OutputConfiguration(surfaceView.getHolder().getSurface());
            OutputConfiguration config2 = new OutputConfiguration(mImageSurface);


            ArrayList<OutputConfiguration> confs = new ArrayList<>();

//            if (pid1.isEmpty() && pid2.isEmpty()) {
//
//            } else {
//                config1.setPhysicalCameraId(pid1);
//                config2.setPhysicalCameraId(pid2);
//            }
//
            confs.add(config1);
            confs.add(config2);

            cameraDevice.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, confs, getActivity().getMainExecutor(), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;

                    startPreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(context, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }));
        } catch (Exception e) {
            // showDialog(Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
    }

    public void startPreview() {
////        showDialog("Success");
//        if (cameraDevice == null) {
////            showDialog("Null");
//            Log.e(TAG, "updatePreview: cameraDevices[id] is null");
//            return;
//        }


//        captureRequestBuilder2.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//             captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, 4f);
//             captureRequestBuilder2.set(CaptureRequest.CONTROL_ZOOM_RATIO, 2f);
        }

        // System.out.println(Arrays.toString(captureRequestBuilder.build().getKeys().toArray()));

        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    // showDialog("working");
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);

                    // session.close();

                    // handler.postDelayed(r, 1000);

                    // createCameraPreviewSession;

                    startPreview();

//                    if (failure.wasImageCaptured())
//                        showDialog("Image was captured");
//                    else
//                        showDialog("image was not captured");

                    System.out.println(failure.getReason());
                    System.out.println("frame " + failure.getFrameNumber());
//                    Log.e(TAG, "Session configuration: " + session.getDeviceStateCallback().toString()); }


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        System.out.println("failed " + failure.getReason() + " ON " + failure.getPhysicalCameraId());
                    } else {
//                        showDialog("failed " + failure.getReason());
                    }
                }

                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);

                    // showDialog("started at " + timestamp);
                }

            }, null);
        } catch (Exception e) {
            // showDialog(Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
    }


    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();

                Image.Plane[] planes = image.getPlanes();

                // The Y (luminance) plane
                ByteBuffer yBuffer = planes[0].getBuffer();
                // The U (chrominance) plane
                ByteBuffer uBuffer = planes[1].getBuffer();
                // The V (chrominance) plane
                ByteBuffer vBuffer = planes[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();

                // Create byte arrays to hold the data
                byte[] yBytes = new byte[ySize];
                byte[] uBytes = new byte[uSize];
                byte[] vBytes = new byte[vSize];

                // Read the buffers into the byte arrays
                yBuffer.get(yBytes);
                uBuffer.get(uBytes);
                vBuffer.get(vBytes);

                // Convert YUV to RGB
                int width = image.getWidth();
                int height = image.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                // You can use a library like YuvImage to simplify YUV to Bitmap conversion
                YuvImage yuvImage = new YuvImage(yBytes, ImageFormat.NV21, width, height, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos);
                byte[] jpegData = baos.toByteArray();
                bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);


                mController.classify(bitmap);

                // Process the image here
//                processImage(image);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    };



    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground1");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
}
