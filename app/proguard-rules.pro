# Navigation destinations are instantiated by their class names from the XML graph.
-keep,allowoptimization class app.trackfit.app.ui.** extends androidx.fragment.app.Fragment {
    public <init>();
}
