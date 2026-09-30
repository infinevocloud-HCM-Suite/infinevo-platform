# Shared Frontend Conventions

> **W-45 Architecture Guide.** Follow these guidelines for all screen and module implementations (`W-46`–`W-48`).

---

## 1. How to Add a Service

Do **not** import `axios` directly or construct URLs with hardcoded hosts.

1. Create a service file in `src/<module>/services/<thing>Service.js`:
   ```javascript
   import { createService } from '@shared/api/createService.js';
   import { apiClient } from '@shared/api/client.js';

   const baseService = createService('/v1/employees');

   export const employeeService = {
     ...baseService,
     // Add domain-specific endpoints as needed:
     getPersonal(id) {
       return apiClient.get(`/v1/employees/${id}/personal`);
     },
     updatePersonal(id, data) {
       return apiClient.put(`/v1/employees/${id}/personal`, data);
     },
   };
   ```
2. In your components, import your service functions instead of calling `apiClient` or `axios` directly.

---

## 2. How to Add a Redux Slice

1. Define your slice in `src/<module>/slices/<thing>Slice.js`:
   ```javascript
   import { createSlice } from '@reduxjs/toolkit';

   export const thingSlice = createSlice({
     name: 'thing',
     initialState: { data: [] },
     reducers: {
       setData(state, action) {
         state.data = action.payload;
       },
     },
   });

   export const { setData } = thingSlice.actions;
   export default thingSlice.reducer;
   ```
2. Export your reducer from `src/<module>/index.js`:
   ```javascript
   import thingReducer from './slices/thingSlice.js';

   export const routes = [ /* your routes */ ];
   export const reducers = {
     thing: thingReducer,
   };
   ```
3. The shell store automatically mounts reducers exported by module index files.

---

## 3. How to Add a Route

1. Define your route object in `src/<module>/routes.js`:
   ```javascript
   import { EmployeeList } from './screens/EmployeeList.jsx';

   export const employeeRoutes = [
     {
       path: '/employees',
       element: <EmployeeList />,
     },
   ];
   ```
2. Export it from `src/<module>/index.js`:
   ```javascript
   import { employeeRoutes } from './routes.js';

   export const routes = [...employeeRoutes];
   ```
3. **Important:** The shell only mounts routes that exist in the server-returned navigation feed (`GET /api/v1/navigation`). An un-entitled route is not mounted in the router.

---

## 4. Theme & Design Tokens

Always obtain colors, dimensions, and radii from `theme.useToken()` or Ant Design component props:
```javascript
import { theme } from 'antd';

export function MyComponent() {
  const { token } = theme.useToken();
  return (
    <div style={{ padding: token.padding, background: token.colorBgContainer }}>
      ...
    </div>
  );
}
```
**Never write literal hex values** (e.g. `#1677ff`) or pixel literals directly in screen styles.

---

## 5. Outcome Dialogs (`msgHelper`)

Use `msgHelper` for action outcomes (success / error modals):
```javascript
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

try {
  await employeeService.create(payload);
  await successMsg('Employee Created', 'The employee record has been saved.');
} catch (err) {
  await errorMsg(err); // Automatically formats message and appends traceId
}
```
For lightweight inline hints, use Ant Design's `message.info(...)`.

---

## 6. The Three Forbidden Things

| Forbidden Pattern | Why It Is Forbidden | ESLint Rule Enforcing It |
|---|---|---|
| **Direct `axios` import** | Bypasses auth token injection, base URL config, and error envelope handling. | `no-restricted-imports` (allowed only in `src/shared/api/client.js`) |
| **Direct `localStorage` / `sessionStorage`** | Violates token abstraction; leads to desynchronized state and security issues. | `no-restricted-globals` & `no-restricted-syntax` |
| **Cross-module import** | Violates module boundaries (e.g. `@hrms` importing from `@payroll`). Modules must only depend on `@shared` and `@core`. | `no-restricted-imports` per module folder |
