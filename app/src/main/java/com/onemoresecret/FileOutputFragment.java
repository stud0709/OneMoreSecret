package com.onemoresecret;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;

import com.onemoresecret.databinding.FragmentFileOutputBinding;
import com.onemoresecret.databinding.FragmentMessageBinding;
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause;

import java.util.Objects;

public class FileOutputFragment extends FragmentWithNotificationBeforePause {
    private FragmentFileOutputBinding binding;

    private Uri uri;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentFileOutputBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    private final View.OnClickListener btnListener = btn -> {
            if (beforePause != null) beforePause.run();

            var intent = new Intent(btn == binding.btnView ? Intent.ACTION_VIEW : Intent.ACTION_SEND);
            var extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            String mimeType = Objects.requireNonNullElse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension), "application/octet-stream");
            if(btn== binding.btnView) {
                intent.setDataAndType(uri, mimeType);
            } else {
                intent.setType(mimeType);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
            }

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            requireActivity().startActivity(intent);
    };

    @Override
    public void setBeforePause(Runnable r) {
        //not necessary
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSend.setOnClickListener(btnListener);
        binding.btnView.setOnClickListener(btnListener);
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }
}