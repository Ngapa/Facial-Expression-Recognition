package com.gtek.fren.ui.aboutemotion;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class AboutEmotionViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public AboutEmotionViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Emosi sering dipandang sebagai respons kompleks yang melibatkan pengalaman subjektif, perubahan fisiologis, dan ekspresi perilaku, yang secara keseluruhan memengaruhi cara individu berinteraksi dengan dunia sekitarnya. emosi manusia dapat dibagi menjadi enam kategori dasar, yaitu bahagia, sedih, takut, jijik, terkejut, dan marah. Teori ini didasarkan pada penelitian lintas budaya yang menunjukkan bahwa ekspresi wajah yang terkait dengan emosi-emosi ini bersifat universal, artinya dapat dikenali di berbagai kelompok budaya di seluruh dunia.");
    }

    public LiveData<String> getText() {
        return mText;
    }
}