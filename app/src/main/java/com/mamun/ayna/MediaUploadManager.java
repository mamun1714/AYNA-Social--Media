package com.mamun.ayna;

import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.UUID;

public class MediaUploadManager {

    private final FirebaseStorage storage;
    private final FirebaseAuth auth;

    public MediaUploadManager() {
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public interface UploadCallback {

        void onProgress(int progress);

        void onSuccess(Uri downloadUrl);

        void onError(Exception exception);
    }

    // Photo Upload
    public UploadTask uploadPhoto(
            Uri fileUri,
            UploadCallback callback) {

        return upload(
                fileUri,
                "photos",
                "image/*",
                callback
        );
    }

    // Video Upload
    public UploadTask uploadVideo(
            Uri fileUri,
            UploadCallback callback) {

        return upload(
                fileUri,
                "videos",
                "video/*",
                callback
        );
    }

    // General Media Upload
    public UploadTask upload(
            Uri fileUri,
            String folder,
            String mimeType,
            UploadCallback callback) {

        if (callback == null) {
            return null;
        }

        if (fileUri == null) {
            callback.onError(
                    new IllegalArgumentException(
                            "ফাইল নির্বাচন করা হয়নি"
                    )
            );
            return null;
        }

        if (folder == null || folder.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException(
                            "Upload folder পাওয়া যায়নি"
                    )
            );
            return null;
        }

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onError(
                    new IllegalStateException(
                            "Upload করার আগে Login করতে হবে"
                    )
            );
            return null;
        }

        try {

            String uid = user.getUid();
            String fileName = UUID.randomUUID().toString();

            String contentType = mimeType;

            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }

            String filePath =
                    "media/" +
                    folder +
                    "/" +
                    uid +
                    "/" +
                    fileName;

            StorageReference fileReference =
                    storage.getReference().child(filePath);

            StorageMetadata metadata =
                    new StorageMetadata.Builder()
                            .setContentType(contentType)
                            .build();

            UploadTask uploadTask =
                    fileReference.putFile(fileUri, metadata);

            // Upload Progress
            uploadTask.addOnProgressListener(snapshot -> {

                long total = snapshot.getTotalByteCount();
                long transferred =
                        snapshot.getBytesTransferred();

                int progress = 0;

                if (total > 0) {
                    progress = (int)
                            ((transferred * 100) / total);
                }

                callback.onProgress(progress);
            });

            // Upload Success
            uploadTask.addOnSuccessListener(snapshot -> {

                fileReference.getDownloadUrl()
                        .addOnSuccessListener(downloadUrl -> {
                            callback.onSuccess(downloadUrl);
                        })
                        .addOnFailureListener(callback::onError);
            });

            // Upload Error
            uploadTask.addOnFailureListener(callback::onError);

            return uploadTask;

        } catch (Exception exception) {

            callback.onError(exception);
            return null;
        }
    }

    // Cancel Upload
    public boolean cancelUpload(UploadTask uploadTask) {

        if (uploadTask != null) {
            return uploadTask.cancel();
        }

        return false;
    }
}
