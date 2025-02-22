package com.gtek.fren.ui.privacypolicy;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PrivacyPolicyViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public PrivacyPolicyViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Kebijakan Privasi\n" +
                "\n" +
                "Kami menghargai privasi Anda dan berkomitmen untuk melindungi data pribadi yang Anda berikan melalui penggunaan aplikasi ini. Kebijakan Privasi ini menjelaskan jenis informasi yang dikumpulkan, cara penggunaannya, serta langkah-langkah yang kami ambil untuk menjaga keamanan data Anda.\n" +
                "\n" +
                "1. Data yang Dikumpulkan\n" +
                "Aplikasi ini hanya mengumpulkan data berupa gambar wajah yang diambil melalui kamera perangkat. Data tersebut digunakan semata-mata untuk keperluan pengenalan dan klasifikasi ekspresi wajah. Tidak ada data lain yang dikumpulkan dari pengguna.\n" +
                "\n" +
                "2. Penggunaan Data\n" +
                "Gambar wajah yang dikumpulkan digunakan secara eksklusif untuk menganalisis dan mengklasifikasikan ekspresi wajah guna menyediakan hasil yang akurat bagi pengguna. Data ini juga membantu dalam evaluasi dan peningkatan performa model pengenalan emosi dalam aplikasi.\n" +
                "\n" +
                "3. Penyimpanan dan Pengolahan Data\n" +
                "Semua data gambar diproses secara lokal di perangkat Anda. Tidak ada data wajah yang dikirim atau disimpan di server eksternal. Hal ini dilakukan untuk memastikan bahwa privasi dan keamanan informasi pribadi Anda tetap terjaga.\n" +
                "\n" +
                "4. Pengungkapan kepada Pihak Ketiga\n" +
                "Kami tidak membagikan atau mengungkapkan data pribadi Anda kepada pihak ketiga. Data yang dikumpulkan hanya digunakan untuk tujuan yang telah dijelaskan di atas, dan tidak akan digunakan untuk tujuan lain tanpa izin dari Anda, kecuali jika diwajibkan oleh hukum.\n" +
                "\n" +
                "5. Hak Pengguna\n" +
                "Anda memiliki hak untuk mengakses, mengoreksi, atau menghapus data pribadi yang telah dikumpulkan oleh aplikasi ini. Jika Anda memiliki pertanyaan atau permintaan terkait data Anda, silakan hubungi kami melalui krilinamar@gmail.com.\n" +
                "\n" +
                "6. Perubahan Kebijakan Privasi\n" +
                "Kebijakan Privasi ini dapat diperbarui secara berkala. Setiap perubahan yang dilakukan akan diinformasikan melalui aplikasi atau situs web resmi kami. Penggunaan aplikasi setelah adanya pembaruan tersebut menunjukkan bahwa Anda telah menyetujui perubahan tersebut.\n" +
                "\n" +
                "Kami berkomitmen untuk menjaga kepercayaan Anda dan memastikan bahwa data pribadi Anda digunakan secara aman dan bertanggung jawab. Jika terdapat pertanyaan lebih lanjut mengenai kebijakan ini, jangan ragu untuk menghubungi kami.");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
