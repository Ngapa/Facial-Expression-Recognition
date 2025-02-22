package com.gtek.fren.ui.about;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class AboutViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public AboutViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Aplikasi ini merupakan implementasi dari sistem pengenalan emosi berbasis Android yang mengintegrasikan algoritma CNN dan KAN untuk mendeteksi serta mengklasifikasikan ekspresi wajah. Aplikasi ini dirancang untuk membandingkan akurasi algoritma CNN dan KAN dalam identifikasi emosi sambil tetap menjaga efisiensi penggunaan sumber daya pada perangkat mobile. Melalui antarmuka yang intuitif, pengguna dapat dengan mudah mengakses informasi tentang emosi yang terdeteksi melalui kamera, yang kemudian dapat diaplikasikan dalam berbagai bidang seperti kesehatan mental, interaksi pengguna, dan sistem rekomendasi. Evaluasi performa aplikasi dilakukan dengan mengukur akurasi, kecepatan komputasi, dan efisiensi memori, yang menunjukkan bahwa kombinasi antara CNN dan KAN memberikan solusi yang menjanjikan untuk pengenalan ekspresi wajah secara efektif dan responsif.");
    }

    public LiveData<String> getText() {
        return mText;
    }
}