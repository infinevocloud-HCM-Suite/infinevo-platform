import { HelmetProvider } from "react-helmet-async";
import AppLayout from "./pages/pageLayouts/appLayout";


function App() {
  return (
    <HelmetProvider>
        <AppLayout />
    </HelmetProvider>
  );
}

export default App;
