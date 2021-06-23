//package ng.dat.ar;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Typeface;
//import android.location.Location;
//import android.opengl.Matrix;
//import android.util.Log;
//import android.view.View;
//
//import androidx.annotation.IdRes;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import ng.dat.ar.helper.LocationHelper;
//import ng.dat.ar.model.ARPoint;
//
//import static ng.dat.ar.helper.Constants.DESTINATION_LAT;
//import static ng.dat.ar.helper.Constants.DESTINATION_LOG;
//import static ng.dat.ar.helper.Constants.DESTINATION_NAME;
//
///**
// * Created by ntdat on 1/13/17.
// */
//
//public class AROverlayView extends View {
//
//    Context context;
//    private float[] rotatedProjectionMatrix = new float[16];
//    private Location currentLocation;
//    private List<ARPoint> arPoints;
//    @IdRes
//    private int destinationIcon;
//
//
//    public AROverlayView(Context context) {
//        super(context);
//
//        this.context = context;
//        setDestinationIcon(R.drawable.ic_foot_steps);
//        //Demo points
//        arPoints = new ArrayList<ARPoint>() {{
//            Log.i("TAG", "instance initializer:" + "start");
//            add(new ARPoint(DESTINATION_NAME, DESTINATION_LAT, DESTINATION_LOG, 0));
//        }};
//
//    }
//
//    public void updateRotatedProjectionMatrix(float[] rotatedProjectionMatrix) {
//        this.rotatedProjectionMatrix = rotatedProjectionMatrix;
//        this.invalidate();
//    }
//
//    public void updateCurrentLocation(Location currentLocation) {
//        this.currentLocation = currentLocation;
//        this.invalidate();
//    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        if (currentLocation == null) {
//            return;
//        }
//
//        final int radius = 30;
//        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        paint.setStyle(Paint.Style.FILL);
//        paint.setColor(Color.WHITE);
//        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
//        paint.setTextSize(60);
//
//        for (int i = 0; i < arPoints.size(); i++) {
//
//            float[] currentLocationInECEF = LocationHelper.WSG84toECEF(currentLocation);
//            float[] pointInECEF = LocationHelper.WSG84toECEF(arPoints.get(i).getLocation());
//            float[] pointInENU = LocationHelper.ECEFtoENU(currentLocation, currentLocationInECEF, pointInECEF);
//
//            float[] cameraCoordinateVector = new float[4];
//            Matrix.multiplyMV(cameraCoordinateVector, 0, rotatedProjectionMatrix, 0, pointInENU, 0);
//
//            // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
//            // if z > 0, the point will display on the opposite
//            if (cameraCoordinateVector[2] < 0) {
//                float x = (0.5f + cameraCoordinateVector[0] / cameraCoordinateVector[3]) * canvas.getWidth();
//                float y = (0.5f - cameraCoordinateVector[1] / cameraCoordinateVector[3]) * canvas.getHeight();
//
//                Bitmap b = BitmapFactory.decodeResource(getResources(), destinationIcon);
//                canvas.drawBitmap(Bitmap.createScaledBitmap(b, 300, 300, false), x - (30 * arPoints.get(i).getName().length() / 2), y - 80, paint);
////                canvas.drawCircle(x, y, radius, paint);
//                canvas.drawText(arPoints.get(i).getName(), x - (30 * arPoints.get(i).getName().length() / 2), y - 100, paint);
//                canvas.drawColor(Color.TRANSPARENT);
//            }
//        }
//    }
//
//    public void setDestinationIcon(int iconResID) {
//        destinationIcon = iconResID;
//    }
//}
