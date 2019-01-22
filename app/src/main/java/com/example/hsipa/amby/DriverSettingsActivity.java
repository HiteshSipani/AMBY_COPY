package com.example.hsipa.amby;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DriverSettingsActivity extends AppCompatActivity {

    private EditText mNameField, mPhoneField, mCarField;
    private Button mBack, mConfirm;
    private ImageView mProfile;
    private FirebaseAuth mAuth;
    private DatabaseReference mDriverDatabase;
    private Uri resultUri;

    private String userID;
    private String mName;
    private String mPhone;
    private String mCar;
    private String mService;
    private String mProfileImageUrl;
    private RadioGroup mRadioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings);

        mNameField=(EditText)findViewById(R.id.name);
        mPhoneField=(EditText)findViewById(R.id.Phone);
        mCarField=(EditText) findViewById(R.id.Car);

        mProfile=(ImageView) findViewById(R.id.profileImage);

        mRadioGroup= (RadioGroup) findViewById(R.id.radioGroup);

        mBack=(Button) findViewById(R.id.back);
        mConfirm=(Button)findViewById(R.id.confirm);


        mAuth= FirebaseAuth.getInstance();
        userID= mAuth.getCurrentUser().getUid();
        mDriverDatabase= FirebaseDatabase.getInstance().getReference().child("Child").child("Drivers").child(userID);

        getUserInfo();
        mProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent= new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent,1);
            }
        });
        try {
            mConfirm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    saveUserInfo();
                }
            });
        }
        catch (Exception ex){
            Log.e("Error on Contact", ex.getMessage());
        }

        mBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                return;
            }
        });
    }
    private void getUserInfo(){
        mDriverDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()&& dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name")!= null){
                        mName=map.get("name").toString();
                        mNameField.setText(mName);
                    }
                    if(map.get("phone")!= null){
                        mPhone=map.get("phone").toString();
                        mPhoneField.setText(mPhone);
                    }
                    if(map.get("car")!= null){
                        mCar=map.get("car").toString();
                        mCarField.setText(mCar);
                    }
                    if(map.get("service")!= null){
                        mService=map.get("service").toString();
                        switch (mService){
                            case "AMBY GO":
                                mRadioGroup.check(R.id.AMBYGO);
                                break;
                            case "AMBY INFY":
                                mRadioGroup.check(R.id.AMBYINFY);
                                break;
                            case "AMBY X   ":
                                mRadioGroup.check(R.id.AMBYX);
                                break;
                            case "AMBY MORF":
                                mRadioGroup.check(R.id.AMBYMORF);
                                break;

                        }
                    }
                    if(map.get("profileImageUrl")!= null){
                        mProfileImageUrl = map.get("profileImageUrl").toString();
                        Glide.with(getApplication()).load(mProfileImageUrl).into(mProfile);
                    }

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void saveUserInfo() {
        mName= mNameField.getText().toString();
        mPhone= mPhoneField.getText().toString();
        mCar= mCarField.getText().toString();

        int selectId = mRadioGroup.getCheckedRadioButtonId();

        final RadioButton radioButton = (RadioButton) findViewById(selectId);

        if( radioButton.getText()== null){
            return;
        }
        mService= radioButton.getText().toString();
        Map userInfo= new HashMap();
        userInfo.put("name",mName);
        userInfo.put("phone",mPhone);
        userInfo.put("car",mCar);
        userInfo.put("service",mService);
        mDriverDatabase.updateChildren(userInfo);

        if(resultUri!=null){
            StorageReference filePath= FirebaseStorage.getInstance().getReference().child("profile_images").child(userID);
            Bitmap bitmap =null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), resultUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG,20,baos);
            byte[] data= baos.toByteArray();
            UploadTask uploadTask= filePath.putBytes(data);
            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    finish();
                    return;
                }
            });
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Task<Uri> downloadUrl= taskSnapshot.getMetadata().getReference().getDownloadUrl();
                    Map newImage= new HashMap();
                    newImage.put("profileImageUrl",downloadUrl.toString());
                    mDriverDatabase.updateChildren(newImage);

                    finish();
                    return;

                }
            });

        }
        else{
            finish();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==1 && resultCode==Activity.RESULT_OK)
        {
            final Uri imageUri= data.getData();
            resultUri=imageUri;
            mProfile.setImageURI(resultUri);
        }
    }
}
